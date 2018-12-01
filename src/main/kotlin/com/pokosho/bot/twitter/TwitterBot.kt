package com.pokosho.bot.twitter

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import kotlin.collections.Map.Entry
import java.util.Properties
import java.util.regex.Matcher
import java.util.regex.Pattern

import net.arnx.jsonic.JSON
import net.java.ao.DBParam
import net.java.ao.Query

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import twitter4j.IDs
import twitter4j.Paging
import twitter4j.ResponseList
import twitter4j.Status
import twitter4j.StatusUpdate
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.TwitterStream
import twitter4j.TwitterStreamFactory
import twitter4j.User
import twitter4j.UserStreamAdapter
import twitter4j.conf.Configuration
import twitter4j.conf.ConfigurationBuilder

import com.atilika.kuromoji.ipadic.Token
import com.pokosho.PokoshoException
import com.pokosho.bot.AbstractBot
import com.pokosho.bot.TFIDF
import com.pokosho.dao.Reply
import com.pokosho.db.Pos
import com.pokosho.db.TableInfo
import com.pokosho.util.StringUtils

/**
 * Twitterから学習し、ツイートするbotの実装
 * @author kaiba
 */
class TwitterBot @Throws(PokoshoException::class)
constructor(dbPropPath: String, botPropPath: String) : AbstractBot(dbPropPath, botPropPath) {
    private var maxReplyCountPerHour = 10
    private var maxReplyIntervalSec = 60 * 60
    // 1回の学習で新しく覚えたTokenのリスト
    private var newTokensFromStudyOnce: MutableList<Token>? = null

    private val twitter: Twitter
    private var consumerKey: String? = null
    private var consumerSecret: String? = null
    private var accessToken: String? = null
    private var accessTokenSecret: String? = null
    private var trendPath: String? = null
    private var notTreacherPath: String? = null
    private var spamWordsPath: String? = null
    private var studyNewWordsFormat: String? = null
    private var studyNewWordsCost = 0
    private var studyNewWordsSay = false
    private var selfUser: User? = null

    init {
        loadProp(botPropPath)

        val builder = ConfigurationBuilder()
        builder.setOAuthConsumerKey(consumerKey)
        builder.setOAuthConsumerSecret(consumerSecret)
        builder.setOAuthAccessToken(accessToken)
        builder.setOAuthAccessTokenSecret(accessTokenSecret)
        val conf = builder.build()
        twitter = TwitterFactory(conf).instance
        try {
            val selfUserID = twitter.id
            selfUser = twitter.showUser(selfUserID)
        } catch (e: IllegalStateException) {
            throw PokoshoException(e)
        } catch (e: TwitterException) {
            throw PokoshoException(e)
        }

    }

    @Throws(PokoshoException::class)
    override fun say(): String? {
        val s = super.say()
        try {
            if (s == null || s.length == 0) {
                log.error("no word")
                return null
            }
            log.info("updateStatus:$s")
            twitter.updateStatus(s)
        } catch (e: TwitterException) {
            PokoshoException(e)
        }

        return s
    }

    @Throws(PokoshoException::class)
    fun reply() {
        if (streamMode) {
            replyStream()
        } else {
            replySeq()
        }
    }

    /**
     * 返信.
     *
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    private fun replySeq() {
        var s: String? = null
        val id = loadLastRead(WORK_LAST_READ_MENTION_FILE)
        val page = Paging()
        page.count = STATUS_MAX_COUNT
        val mentionList: ResponseList<Status>
        val selfID: Long
        try {
            selfID = twitter.id
            mentionList = twitter.mentionsTimeline
        } catch (e1: TwitterException) {
            throw PokoshoException(e1)
        }

        val last = mentionList[0]
        saveLastRead(last.id, WORK_LAST_READ_MENTION_FILE)
        for (from in mentionList) {
            try {
                if (from.id <= id) {
                    log.debug("found last mention id:$id")
                    break
                }
                if (from.user.id == selfID)
                    continue
                var fromfrom: Status? = null
                if (0 < from.inReplyToStatusId) {
                    fromfrom = twitter.showStatus(from.inReplyToStatusId)
                }
                // リプライ元、リプライ先を連結してもっともコストが高い単語を使う
                s = from.text
                if (fromfrom != null) {
                    s = s + "。" + fromfrom.text
                }
                // @xxx を削除
                s = TwitterUtils.removeMention(s)
                s = super.say(s, NUMBER_OF_DOCUMENT)
                if (s == null || s.length == 0) {
                    log.error("no word")
                    continue
                }
                log.info("updateStatus:$s")
                val us = StatusUpdate(
                    "@"
                            + from.user.screenName + " " + s
                )
                us.inReplyToStatusId = from.id
                twitter.updateStatus(us)
            } catch (e: TwitterException) {
                PokoshoException(e)
            }

        }
    }

    fun studyNewWordsEnabled(): Boolean {
        return studyNewWordsSay
    }

    /**
     * 返信(stream).
     *
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    private fun replyStream() {
        val builder = ConfigurationBuilder()
        builder.setOAuthConsumerKey(consumerKey)
        builder.setOAuthConsumerSecret(consumerSecret)
        builder.setOAuthAccessToken(accessToken)
        builder.setOAuthAccessTokenSecret(accessTokenSecret)
        val conf = builder.build()
        val factory = TwitterStreamFactory(conf)
        val twitterStream = factory.getInstance()
        try {
            twitterStream
                .addListener(MentionEventListener(selfUser!!.id))
        } catch (e: TwitterException) {
            throw PokoshoException(e)
        }

        // start streaming
        log.info("start twitterStream.user() ------------------------------")
        twitterStream.user()
    }

    /**
     * HomeTimeLineから学習する.
     */
    @Throws(PokoshoException::class)
    override fun study(str: String?) {
        val friends: IDs
        val follower: IDs
        val trendCountMap = HashMap<String, Int>()
        this.newTokensFromStudyOnce = ArrayList<Token>()
        log.info("start study ------------------------------------")
        try {
            // WORKファイルのタイムスタンプを見て、指定時間経っていたら、フォロー返しを実行
            val f = File(WORK_LAST_FOLLOW_FILE)
            val cTime = System.currentTimeMillis()
            spamWords = TwitterUtils.getStringSet(spamWordsPath)
            log.info(
                "selfUser:" + selfUser + " currentTimeMillis:" + cTime
                        + " lastModified+FOLLOW_INTERVAL_MSEC:"
                        + (f.lastModified() + FOLLOW_INTERVAL_MSEC)
            )
            if (!f.exists() || f.lastModified() + FOLLOW_INTERVAL_MSEC < cTime) {
                friends = twitter.getFriendsIDs(selfUser!!.id, -1)
                follower = twitter.getFollowersIDs(selfUser!!.id, -1)
                val notFollowIdList = calcNotFollow(follower, friends)
                doFollow(notFollowIdList)
            }

            // HomeTimeLine取得
            val id = loadLastRead(WORK_LAST_READ_FILE)
            val page = Paging()
            page.count = STATUS_MAX_COUNT
            val homeTimeLineList = twitter
                .getHomeTimeline(page)
            val last = homeTimeLineList[0]
            saveLastRead(last.id, WORK_LAST_READ_FILE)
            log.info("size of homeTimelineList:" + homeTimeLineList.size)
            val endsWithNumPattern = Pattern.compile(
                ".*[0-9]+$",
                Pattern.CASE_INSENSITIVE
            )
            val notTeachers = TwitterUtils
                .getNotTeachers(notTreacherPath)
            for (s in homeTimeLineList) {
                if (!DEBUG) {
                    if (s.id <= id) {
                        log.info("found last tweet. id:$id")
                        break
                    }
                }
                if (notTeachers.contains(s.user.id)) {
                    log.debug("not teacher:" + s.user.id)
                    continue
                }
                try {
                    if (s.user.id == selfUser!!.id)
                        continue
                    var tweet = s.text
                    if (TwitterUtils.isSpamTweet(tweet)) {
                        log.debug(
                            "spam:" + tweet + " user:"
                                    + s.user.screenName
                        )
                        continue
                    }
                    tweet = TwitterUtils.removeHashTags(tweet)
                    tweet = TwitterUtils.removeUrl(tweet)
                    tweet = TwitterUtils.removeMention(tweet)
                    tweet = TwitterUtils.removeRTString(tweet)
                    val splited = tweet.split("。".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    // 「。」で切れたところで文章の終わりとする
                    for (msg in splited) {
                        // トレンド用の集計
                        val token = studyFromLine(msg)
                        this.newTokensFromStudyOnce!!.addAll(this.newTokens!!)
                        if (token != null && 0 < token.size) {
                            for (t in token) {
                                if (StringUtils
                                        .toPos(t.getAllFeaturesArray()[StringUtils.KUROMOJI_POS_INDEX]) == Pos.Noun
                                    && TwitterUtils.containsJPN(
                                        t
                                            .getSurface()
                                    )
                                    && !NOT_TREND!!.contains(
                                        t
                                            .getSurface()
                                    )
                                    && 1 < t.getSurface().length()
                                ) {
                                    var count = 0
                                    if (trendCountMap.containsKey(
                                            t
                                                .getSurface()
                                        )
                                    ) {
                                        count = trendCountMap[t
                                            .getSurface()]
                                    }
                                    count++
                                    trendCountMap[t.getSurface()] = count
                                }
                            }
                        }
                        // 数字で終わるtweetは誰が教えているのか？
                        val matcher = endsWithNumPattern.matcher(msg)
                        if (matcher.matches()) {
                            log.info(
                                "found endswith number tweet:"
                                        + s.text + " tweetID:" + s.id
                            )
                        }
                    }
                } catch (e: IOException) {
                    log.error("io error", e)
                } catch (e: SQLException) {
                    log.error("sql error", e)
                }

            }
            createTrend(trendCountMap)
        } catch (e: TwitterException) {
            log.error("twitter error", e)
        }

        log.info("end study ------------------------------------")
    }

    private fun saveLastRead(id: Long, path: String) {
        var pw: PrintWriter? = null
        var bw: BufferedWriter? = null
        var filewriter: FileWriter? = null
        try {
            val file = File(path)
            filewriter = FileWriter(file)
            bw = BufferedWriter(filewriter)
            pw = PrintWriter(bw)
            pw.print(id)
        } catch (e: IOException) {
            log.error(e.toString())
        } finally {
            try {
                pw?.close()
                bw?.close()
                filewriter?.close()
            } catch (e: Exception) {
                log.error(e.toString())
            }

        }
    }

    private fun loadLastRead(path: String): Long {
        var id: Long = 0
        var filereader: FileReader? = null
        var br: BufferedReader? = null
        try {
            val file = File(path)
            if (!file.exists())
                return 0
            filereader = FileReader(file)
            br = BufferedReader(filereader)
            id = java.lang.Long.valueOf(br.readLine())
        } catch (e: IOException) {
            log.error("io error", e)
        } catch (e: NumberFormatException) {
            log.error("number format error", e)
        } finally {
            try {
                br?.close()
                filereader?.close()
            } catch (e: IOException) {
                log.error("io error", e)
            }

        }
        return id
    }

    @Throws(PokoshoException::class)
    private fun loadProp(propPath: String) {
        val prop = Properties()
        try {
            FileInputStream(propPath).use { `is` ->
                InputStreamReader(`is`, "UTF-8").use { isr ->
                    BufferedReader(isr).use { reader ->
                        prop.load(reader)
                        consumerKey = prop.getProperty(KEY_CONSUMER_KEY)
                        consumerSecret = prop.getProperty(KEY_CONSUMER_SECRET)
                        accessToken = prop.getProperty(KEY_ACCESS_TOKEN)
                        accessTokenSecret = prop.getProperty(KEY_ACCESS_TOKEN_SECRET)
                        trendPath = prop.getProperty(KEY_TREND_PATH)
                        notTreacherPath = prop.getProperty(KEY_NOT_TEACHER_PATH)
                        spamWordsPath = prop.getProperty(KEY_SPAM_WORDS)
                        studyNewWordsFormat = prop.getProperty(KEY_STUDY_NEW_WORDS_FORMAT)
                        studyNewWordsCost = Integer.parseInt(prop.getProperty(KEY_STUDY_NEW_WORDS_COST))
                        studyNewWordsSay = Integer.parseInt(prop.getProperty(KEY_STUDY_NEW_WORDS_SAY)) == 1
                        maxReplyCountPerHour = Integer.parseInt(
                            prop
                                .getProperty(KEY_MAX_REPLY_COUNT)
                        )
                        maxReplyIntervalSec = Integer.parseInt(
                            prop
                                .getProperty(KEY_REPLY_INTERVAL_MIN)
                        )
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            log.error("file not found error", e)
            throw PokoshoException(e)
        } catch (e: IOException) {
            log.error("io error", e)
            throw PokoshoException(e)
        }

    }

    private fun doFollow(notFollowIdList: List<Long>) {
        for (userId in notFollowIdList) {
            var user: User? = null
            try {
                user = twitter.showUser(userId)
                if (isSpamUser(user!!)) {
                    continue
                }
                user = twitter.createFriendship(userId)
                if (user == null) {
                    log.error("failed to follow：$userId")
                }
                log.info("followed:" + user!!.name)
            } catch (e: TwitterException) {
                log.error("twitter error", e)
            }

        }
        if (0 < notFollowIdList.size) {
            saveLastRead(
                notFollowIdList[notFollowIdList.size - 1],
                WORK_LAST_FOLLOW_FILE
            )
        }
    }

    /**
     * スパム判定.
     *
     * @param user
     * @return
     */
    private fun isSpamUser(user: User): Boolean {
        val prof = user.description
        if (prof == null || prof.length == 0) {
            log.info(
                SPAM_USER_LOG_LABEL + user.screenName + " "
                        + user.id + " has not profile."
            )
            return true
        }
        if (!TwitterUtils.containsJPN(prof)) {
            log.info(
                SPAM_USER_LOG_LABEL + user.screenName + " "
                        + user.id + " has not profile in Japanese."
            )
            return true
        }
        if (user.statusesCount < MIN_TWEET_FOR_FOLLOW) {
            log.info(
                SPAM_USER_LOG_LABEL + user.screenName + " "
                        + user.id + " tweets few"
            )
            return true
        }
        for (w in spamWords!!) {
            if (0 < prof.indexOf(w)) {
                log.info(
                    SPAM_USER_LOG_LABEL + user.screenName + " "
                            + user.id + " has spam words:" + w
                )
                return true
            }
        }
        return false
    }

    private fun calcNotFollow(follower: IDs, friends: IDs): List<Long> {
        val returnValue = ArrayList<Long>()
        val lastFollow = loadLastRead(WORK_LAST_FOLLOW_FILE)
        log.info(
            "follower count:" + follower.iDs.size
                    + " friends count:" + friends.iDs.size
        )
        for (id in follower.iDs) {
            if (lastFollow == id)
                break // 最後にフォローしたところまで読んだ
            if (!contains(friends, id)) {
                log.info("follow user id:$id")
                returnValue.add(java.lang.Long.valueOf(id))
            }
        }
        return returnValue
    }

    private fun contains(friends: IDs, id: Long): Boolean {
        for (frendId in friends.iDs) {
            if (frendId == id) {
                return true
            }
        }
        return false
    }

    /**
     * トレンドを作成.
     *
     * @param trendCountMap
     */
    private fun createTrend(trendCountMap: Map<String, Int>) {
        log.debug("createTrend trendCountMap.size:" + trendCountMap.size)
        val trends = Trend()
        trendCountMap.entries
        val entries = ArrayList(
            trendCountMap.entries
        )
        Collections.sort(entries) { o1, o2 -> o2.value - o1.value }
        val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val dateStr = sdf.format(Date())
        var i = 0
        while (i < TREND_COUNT_MAX && i < entries.size) {
            val entry = entries[i]
            trends.addTrend(dateStr, entry.key, null, null, null)
            log.debug(
                "trend rank " + i + ":" + entry.key + "("
                        + entry.value
            )
            i++
        }
        val json = JSON.encode(trends)
        // output to file
        var fw: FileWriter? = null
        try {
            fw = FileWriter(File(trendPath!!))
            fw.write(json)
        } catch (e: IOException) {
            log.error("outputing trends failed", e)
        } finally {
            try {
                fw!!.close()
            } catch (e: Exception) {
                log.error("closing trends file failed", e)
            }

        }
    }

    /**
     * 新しく学習した単語をツイートする
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun sayNewWords() {
        if (this.newTokensFromStudyOnce == null || this.newTokensFromStudyOnce!!.size == 0) {
            log.info("no new words")
            return
        }

        val sb = StringBuilder()
        for (t in this.newTokensFromStudyOnce!!) {
            val word = t.getSurface()
            // 名詞以外は新しく覚えたよツイートしない
            if (StringUtils.toPos(t.getAllFeaturesArray()[StringUtils.KUROMOJI_POS_INDEX]) != Pos.Noun) {
                return
            }
            val tfidf = TFIDF.calculateTFIDF(AbstractBot.manager, null, t.getSurface(), NUMBER_OF_DOCUMENT.toLong())
            log.debug("sayNewWords:" + t.getSurface() + " tfidf cost:" + tfidf)
            if (tfidf < this.studyNewWordsCost) {
                log.debug("it isn't a important word:$word")
            }
            sb.append(word)
            sb.append('、')
        }
        sb.deleteCharAt(sb.length - 1)
        val newWordsTweet = String.format(
            this.studyNewWordsFormat!!,
            sb.toString()
        )
        if (newWordsTweet != null && newWordsTweet.length <= MAX_TWEET_LENTGH) {
            try {
                twitter.updateStatus(newWordsTweet)
            } catch (e: TwitterException) {
                log.error("update status failed:", e)
            }

        } else {
            log.warn("over tweet length:$newWordsTweet")
        }
    }

    /**
     * Mentionリスナ
     *
     * @author kaiba
     */
    private inner class MentionEventListener @Throws(TwitterException::class)
    constructor(private val selfUser: Long) : UserStreamAdapter() {
        private val selfScreenName: String

        init {
            selfScreenName = twitter.showUser(selfUser).screenName
        }

        fun onStatus(from: Status) {
            super.onStatus(from)
            val tweet = from.text
            var s: String? = null
            if (!tweet.contains("@$selfScreenName")) {
                return
            }
            log.info(
                "onStatus user:" + from.user.screenName + " tw:"
                        + tweet
            )
            // reply超過チェック
            try {
                // ユーザの一時間以内のreplyを取得
                val reply = AbstractBot.manager.find(
                    Reply::class.java,
                    Query.select().where(
                        TableInfo.TABLE_REPLY_USER_ID + " = ? and "
                                + TableInfo.TABLE_REPLY_TIME + " > ?",
                        from.user.id,
                        System.currentTimeMillis() / 1000 - maxReplyIntervalSec
                    )
                )
                if (reply != null) {
                    log.debug("reply count:" + reply.size)
                    if (reply != null && maxReplyCountPerHour < reply.size) {
                        log.debug(
                            "user:" + from.user.screenName
                                    + " sent reply over " + maxReplyCountPerHour
                        )
                        return
                    }
                }
            } catch (e: SQLException) {
                log.error("sql error", e)
                return
            }

            try {
                log.info("start getId")
                if (from.user.id == selfUser) {
                    log.info("reply to self. nothing todo")
                    return
                }
                log.info("end getId")
                var fromfrom: Status? = null
                if (0 < from.inReplyToStatusId) {
                    log.info("start showStatus")
                    fromfrom = twitter.showStatus(from.inReplyToStatusId)
                    log.info("end showStatus")
                }
                // リプライ元、リプライ先を連結してもっともコストが高い単語を使う
                s = from.text
                if (fromfrom != null) {
                    s = s + "。" + fromfrom.text
                }
                // @xxx を削除
                s = TwitterUtils.removeMention(s)
                s = TwitterUtils.removeRTString(s!!)
                s = TwitterUtils.removeUrl(s)
                s = TwitterUtils.removeHashTags(s)
                log.info("start say against:" + s!!)
                s = say(s, NUMBER_OF_DOCUMENT)
                log.info("end say")
                if (s == null || s.length == 0) {
                    log.info("no word")
                    return
                }
                log.info("updateStatus:$s")
                val us = StatusUpdate(
                    "@"
                            + from.user.screenName + " " + s
                )
                us.inReplyToStatusId = from.id
                twitter.updateStatus(us)
            } catch (e: TwitterException) {
                log.error("twitter error", e)
            } catch (e: PokoshoException) {
                log.error("system error", e)
            } catch (e: Exception) {
                log.error("system error(debug)", e)
            }

            // insert reply
            try {
                AbstractBot.manager.create(
                    Reply::class.java,
                    DBParam(
                        TableInfo.TABLE_REPLY_TWEET_ID, from
                            .id
                    ),
                    DBParam(
                        TableInfo.TABLE_REPLY_USER_ID, from
                            .user.id
                    ),
                    DBParam(
                        TableInfo.TABLE_REPLY_TIME, (System
                            .currentTimeMillis() / 1000).toInt()
                    )
                )
            } catch (e: SQLException) {
                log.error("insert reply error", e)
            }

        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TwitterBot::class.java)
        private val DB_PROP = "./conf/db.properties"
        private val BOT_PROP = "./conf/bot.properties"
        private val LOG_PROP = "./conf/log.properties"
        private val DEBUG = false // かならず全処理を行う
        private val WORK_LAST_READ_FILE = "waketi_last_read.txt"
        private val WORK_LAST_READ_MENTION_FILE = "waketi_last_read_mention.txt"
        private val WORK_LAST_FOLLOW_FILE = "waketi_last_follow.txt"
        private val FOLLOW_INTERVAL_MSEC = 60 * 60 * 3 * 1000 // フォロー返しの間隔
        private val STATUS_MAX_COUNT = 200
        private val streamMode = true
        // TF-IDFのN. 以前のコストと比較するわけではないので定数で良い
        private val NUMBER_OF_DOCUMENT = 100000
        private var NOT_TREND: MutableSet<String>? = null
        private var spamWords: Set<String>? = null
        private val MIN_TWEET_FOR_FOLLOW = 100
        private val SPAM_USER_LOG_LABEL = "spam user:"
        private val MAX_TWEET_LENTGH = 140
        private val TREND_COUNT_MAX = 10

        init {
            NOT_TREND = HashSet()
            NOT_TREND!!.add("の")
            NOT_TREND!!.add("を")
            NOT_TREND!!.add("こと")
        }

        private val KEY_CONSUMER_KEY = "twitter.consumer.key"
        private val KEY_CONSUMER_SECRET = "twitter.consumer.secret"
        private val KEY_ACCESS_TOKEN = "twitter.access.token"
        private val KEY_ACCESS_TOKEN_SECRET = "twitter.access.secret"
        private val KEY_TREND_PATH = "com.pokosho.trends"
        private val KEY_NOT_TEACHER_PATH = "com.pokosho.not_teacher"
        private val KEY_MAX_REPLY_COUNT = "com.pokosho.max_reply_count"
        private val KEY_REPLY_INTERVAL_MIN = "com.pokosho.max_reply_interval_sec"
        private val KEY_SPAM_WORDS = "com.pokosho.spamwords"
        private val KEY_STUDY_NEW_WORDS_FORMAT = "com.pokosho.study_new_words_format"
        private val KEY_STUDY_NEW_WORDS_COST = "com.pokosho.study_new_words_cost"
        private val KEY_STUDY_NEW_WORDS_SAY = "com.pokosho.study_new_words_say"

        /**
         * ホームタイムラインから学習し、1回ツイートします.
         *
         * @param args
         */
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                System.setProperty("file.encoding", StringUtils.ENCODE_STRING)
                System.setProperty("java.util.logging.config.file", LOG_PROP)
                System.setProperty(
                    "twitter4j.loggerFactory",
                    "twitter4j.internal.logging.NullLoggerFactory"
                )

                val b = TwitterBot(DB_PROP, BOT_PROP)
                if (0 < args.size) {
                    val mode = args[0]
                    if (mode == "-c") {
                        b.cleaning()
                    } else if (mode == "-s") {
                        b.reply()
                    }
                } else {
                    b.study(null)
                    b.say()
                    if (b.studyNewWordsSay) {
                        b.sayNewWords()
                    }
                }
            } catch (e: Exception) {
                log.error("system error", e)
            }

        }
    }
}
