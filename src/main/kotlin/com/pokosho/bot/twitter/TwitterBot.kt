package com.pokosho.bot.twitter

import com.pokosho.PokoshoException
import com.pokosho.bot.AbstractBot
import com.pokosho.util.StringUtils
import org.slf4j.LoggerFactory
import twitter4j.*
import twitter4j.conf.ConfigurationBuilder
import java.io.*
import java.sql.SQLException
import java.util.*
import java.util.regex.Pattern

/**
 * Twitterから学習し、ツイートするbotの実装
 * @author kaiba
 */
class TwitterBot @Throws(PokoshoException::class)
constructor(dbPropPath: String, botPropPath: String) : AbstractBot(dbPropPath, botPropPath) {
    private var maxReplyCountPerHour = 10
    private var maxReplyIntervalSec = 60 * 60
    private val twitter: Twitter
    private var consumerKey: String? = null
    private var consumerSecret: String? = null
    private var accessToken: String? = null
    private var accessTokenSecret: String? = null
    private var trendPath: String? = null
    private var notTreacherPath: String? = null
    private var spamWordsPath: String? = null
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
                log.error("This bot knows no words")
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
        var s: String?
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
                s = TwitterUtils.removeMention(s!!)
                s = super.say(s, NUMBER_OF_DOCUMENT)
                if (s == null || s.length == 0) {
                    log.error("Failed to reply. This bot knows no words")
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

    /**
     * HomeTimeLineから学習する.
     */
    @Throws(PokoshoException::class)
    override fun study(str: String?) {
        val friends: IDs
        val follower: IDs
        log.info("start study ------------------------------------")
        try {
            // WORKファイルのタイムスタンプを見て、指定時間経っていたら、フォロー返しを実行
            val f = File(WORK_LAST_FOLLOW_FILE)
            val cTime = System.currentTimeMillis()
            if (spamWordsPath != null) {
                spamWords = TwitterUtils.getStringSet(spamWordsPath!!)
            }
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
            val endsWithNumPattern = Pattern.compile(".*[0-9]+$", Pattern.CASE_INSENSITIVE)
            val notTeachers = TwitterUtils
                .getNotTeachers(notTreacherPath!!)
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
                    val split = tweet.split("。".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    // 「。」で切れたところで文章の終わりとする
                    for (msg in split) {
                        studyFromLine(msg)
                        // 数字で終わるtweetは誰が教えているのか？
                        val matcher = endsWithNumPattern.matcher(msg)
                        if (matcher.matches()) {
                            log.info("found endswith number tweet:" + s.text + " tweetID:" + s.id)
                        }
                    }
                } catch (e: IOException) {
                    log.error("io error", e)
                } catch (e: SQLException) {
                    log.error("sql error", e)
                }
            }
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
            var user: User?
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
        log.info("follower count:${follower.iDs.size} friends count: ${friends.iDs.size}")
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
        for (friendId in friends.iDs) {
            if (friendId == id) return true
        }
        return false
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
        // TF-IDFのN. 以前のコストと比較するわけではないので定数で良い
        private val NUMBER_OF_DOCUMENT = 100000
        private var spamWords: Set<String> = setOf()
        private val MIN_TWEET_FOR_FOLLOW = 100
        private val SPAM_USER_LOG_LABEL = "spam user:"
        private val KEY_CONSUMER_KEY = "twitter.consumer.key"
        private val KEY_CONSUMER_SECRET = "twitter.consumer.secret"
        private val KEY_ACCESS_TOKEN = "twitter.access.token"
        private val KEY_ACCESS_TOKEN_SECRET = "twitter.access.secret"
        private val KEY_TREND_PATH = "com.pokosho.trends"
        private val KEY_NOT_TEACHER_PATH = "com.pokosho.not_teacher"
        private val KEY_MAX_REPLY_COUNT = "com.pokosho.max_reply_count"
        private val KEY_REPLY_INTERVAL_MIN = "com.pokosho.max_reply_interval_sec"
        private val KEY_SPAM_WORDS = "com.pokosho.spamwords"

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
                    }
                    if (mode == "-s") {
                        b.reply()
                    }
                    return
                }
                b.study(null)
                b.say()
            } catch (e: Exception) {
                log.error("system error", e)
            }
        }
    }
}
