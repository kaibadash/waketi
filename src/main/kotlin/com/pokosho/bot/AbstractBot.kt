package com.pokosho.bot

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.sql.SQLException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.Properties

import net.java.ao.DBParam
import net.java.ao.EntityManager
import net.java.ao.Query

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.pokosho.PokoshoException
import com.pokosho.bot.twitter.TwitterUtils
import com.pokosho.dao.Chain
import com.pokosho.dao.Word
import com.pokosho.db.DBUtil
import com.pokosho.db.Pos
import com.pokosho.db.TableInfo
import com.pokosho.util.StrRep
import com.pokosho.util.StringUtils

abstract class AbstractBot @Throws(PokoshoException::class)
constructor(dbPropPath: String, botPropPath: String) {
    protected var tokenizer: Tokenizer? = null
    protected var strRep: StrRep
    protected var prop: Properties
    private var useChikuwa = false
    // 新しく学習したToken
    private var newTokens: MutableList<Token>? = null

    init {
        prop = Properties()
        log.debug("dbPropPath:$dbPropPath")
        log.debug("botPropPath:$botPropPath")
        try {
            prop.load(FileInputStream(botPropPath))
        } catch (e1: FileNotFoundException) {
            log.error("FileNotFoundException", e1)
            throw PokoshoException(e1)
        } catch (e1: IOException) {
            log.error("IOException", e1)
            throw PokoshoException(e1)
        }

        strRep = StrRep(prop.getProperty("com.pokosho.repstr"))
        try {
            manager = DBUtil.getEntityManager(dbPropPath)
        } catch (e: IllegalArgumentException) {
            log.error("system error", e)
            throw PokoshoException(e)
        }

        this.tokenizer = this.loadTokenizer(prop.getProperty("com.pokosho.custom_dic"))
    }

    @Throws(PokoshoException::class)
    private fun loadTokenizer(pathToCustomDictionary: String): Tokenizer {
        try {
            tokenizer = Tokenizer.Builder().userDictionary(
                FileInputStream(pathToCustomDictionary)
            ).build()
        } catch (ioe: IOException) {
            log.debug("failed to load cutom dictionary. use default tokenizer.", ioe)
        }

        if (tokenizer == null) {
            tokenizer = Tokenizer()
        }
        return tokenizer
    }

    /**
     * 学習する.
     *
     * @param file
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    abstract fun study(str: String)

    /**
     * 発言を返す.
     *
     * @return 発言.
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    open fun say(): String? {
        var result: String? = null
        try {
            val chain = manager.find(
                Chain::class.java,
                Query.select()
                    .where(TableInfo.TABLE_CHAIN_START + " = ?", true)
                    .order("rand() limit 1")
            )
            if (chain == null || chain.size == 0) {
                log.info("bot knows no words.")
                return null
            }
            // 終了まで文章を組み立てる
            val idList = LinkedList(
                createWordIDList(chain)
            )
            result = createWordsFromIDList(idList)
            result = strRep.rep(result)
        } catch (e: SQLException) {
            throw PokoshoException(e)
        }

        return result
    }

    /**
     * 返事をする.
     *
     * @param from
     * 返信元メッセージ.
     * @return 返事.
     */
    @Synchronized
    @Throws(PokoshoException::class)
    fun say(from: String, numberOfDocuments: Int): String? {
        var from = from
        try {
            log.debug("reply base:$from")
            from = StringUtils.simplizeForReply(from)
            log.debug("simplizeForReply:$from")
            val token = tokenizer!!.tokenize(from) ?: return null
            val tokenCount = HashMap<String, Int>() // 頻出単語
            var maxCount = 0
            var maxTFIDF = 0.0
            var keyword: Token? = null
            var maxNounCountToken: Token? = null
            for (t in token) {
                log.debug(
                    "surface:" + t.getSurface() + " features:"
                            + t.getAllFeatures()
                )
                // 名詞でtf-idfが高い言葉
                val tPos = StringUtils
                    .toPos(t.getAllFeaturesArray()[StringUtils.KUROMOJI_POS_INDEX])
                if (tPos == Pos.Noun) {
                    val tdidf = TFIDF.calculateTFIDF(
                        manager, from,
                        t.getSurface(), numberOfDocuments.toLong()
                    )
                    if (maxTFIDF < tdidf) {
                        maxTFIDF = tdidf
                        keyword = t
                    }
                    // 出現回数のカウント
                    if (!tokenCount.containsKey(t.getSurface())) {
                        tokenCount[t.getSurface()] = 1
                    } else {
                        val c = tokenCount[t.getSurface()] + 1
                        if (maxCount < c) {
                            maxCount = c
                            tokenCount[t.getSurface()] = c
                            maxNounCountToken = t
                        }
                    }
                }
            }

            // 最大コストの単語で始まっているか調べて、始まっていたら使う
            var word: Array<Word>? = null
            if (0 < maxTFIDF) {
                word = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " = ?",
                        keyword!!.getSurface()
                    )
                )
            }
            if ((word == null || word.size == 0) && maxNounCountToken != null) {
                log.debug("keyword isn't found. use max count token:" + maxNounCountToken!!.getSurface())
                word = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " = ?",
                        maxNounCountToken!!.getSurface()
                    )
                )
            }
            if (word == null || word.size == 0) {
                log.debug("keyword isn't found. can't reply.")
                return null
            }

            // word found, start creating chain
            var chain: Array<Chain>? = manager.find(
                Chain::class.java,
                Query.select().where(
                    TableInfo.TABLE_CHAIN_START + " = ? and "
                            + TableInfo.TABLE_CHAIN_PREFIX01
                            + " = ?  order by rand() limit 1", true,
                    word[0].word_ID
                )
            )
            var startWithMaxCountWord = false
            if (chain == null || chain.size == 0) {
                chain = manager.find(
                    Chain::class.java,
                    Query.select().where(
                        TableInfo.TABLE_CHAIN_PREFIX01 + " = ?  order by rand() limit 1",
                        word[0].word_ID
                    )
                )
                if (chain == null || chain.size == 0) {
                    // まずあり得ないが保険
                    log.debug("chain which is prefix01 or suffix wasn't found:" + word[0].word_ID!!)
                    return null
                }
            } else {
                startWithMaxCountWord = true
            }

            // 終了まで文章を組み立てる
            var idList = LinkedList(
                createWordIDList(chain)
            )
            if (!startWithMaxCountWord) {
                // 先頭まで組み立てる
                idList = createWordIDListEndToStart(idList, chain)
            }
            var result = createWordsFromIDList(idList)
            result = strRep.rep(result)

            log.info(
                from
                        + " => "
                        + result
                        + " tfidf:"
                        + (if (keyword != null) keyword!!.getSurface() else "null")
                        + " max noun count:"
                        + if (maxNounCountToken != null)
                    maxNounCountToken!!
                        .getSurface()
                else
                    "null"
            )
            return result
        } catch (e: SQLException) {
            throw PokoshoException(e)
        }

    }

    @Throws(IOException::class, SQLException::class)
    protected fun studyFromLine(str: String?): List<Token>? {
        var str = str
        log.info("studyFromLine:" + str!!)
        // スパム判定
        if (str == null || str.length < 0) {
            return null
        }
        if (!TwitterUtils.containsJPN(str)) {
            log.debug("it's not Japanese")
            return null
        }
        // TODO:AbstractBoxにTweetの処理が来るのはおかしい… TwitterBotでやるべき
        if (TwitterUtils.isSpamTweet(str)) {
            log.debug("spam tweet:$str")
            return null
        }
        str = StringUtils.simplize(str)
        val token = tokenizer!!.tokenize(str) ?: return null
        val chainTmp = arrayOfNulls<Int>(CHAIN_COUNT)
        this.newTokens = ArrayList<Token>(token.size)
        // chainを作成する。
        // chainの作成確認のため、拡張for文は使わない。
        for (i in token.indices) {
            log.debug(token[i].getSurface())
            var existWord = manager.find(
                Word::class.java,
                Query.select().where(
                    TableInfo.TABLE_WORD_WORD + " = ?",
                    token[i].getSurface()
                )
            )
            if (existWord == null || existWord.size == 0) {
                // 新規作成
                val newWord = manager.create(Word::class.java)
                newWord.word = token[i].getSurface()
                newWord.word_Count = 1
                newWord.pos_ID = StringUtils
                    .toPos(token[i].getAllFeaturesArray()[StringUtils.KUROMOJI_POS_INDEX])
                    .intValue
                newWord.time = (System.currentTimeMillis() / 1000).toInt()
                newWord.save()

                // 新しく学習した単語を保持
                this.newTokens!!.add(token[i])

                // IDを取得
                existWord = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " = ?",
                        token[i].getSurface()
                    )
                )
                // createで作っている時点でIDは分かるので無駄…
            } else {
                existWord[0].word_Count = existWord[0].word_Count!! + 1
                existWord[0].time = (System.currentTimeMillis() / 1000).toInt()
                existWord[0].save()
            }

            // chainができているかどうか
            if (2 < i) {
                // swap
                chainTmp[0] = chainTmp[1]
                chainTmp[1] = chainTmp[2]
                chainTmp[2] = existWord!![0].word_ID
                createChain(chainTmp[0], chainTmp[1], chainTmp[2], false)
            } else {
                // Chainを準備
                if (chainTmp[0] != null) {
                    if (chainTmp[1] != null) {
                        chainTmp[2] = existWord!![0].word_ID // chain 完成
                        createChain(chainTmp[0], chainTmp[1], chainTmp[2], true)
                    } else {
                        chainTmp[1] = existWord!![0].word_ID
                    }
                } else {
                    chainTmp[0] = existWord!![0].word_ID
                }
            }
        }
        // EOF
        createChain(chainTmp[1], chainTmp[2], null, false)
        return token
    }

    @Throws(SQLException::class)
    protected fun createChain(
        prefix01: Int?, prefix02: Int?,
        safix: Int?, start: Boolean?
    ) {
        log.debug(
            String.format(
                "createChain:%d,%d,%d", prefix01, prefix02,
                safix
            )
        )
        if (prefix01 == null || prefix02 == null) {
            log.debug("prefix is null.")
            return
        }
        val existChain: Array<Chain>?
        existChain = manager.find(
            Chain::class.java,
            Query.select().where(
                TableInfo.TABLE_CHAIN_PREFIX01 + "=? and "
                        + TableInfo.TABLE_CHAIN_PREFIX02 + "=? and "
                        + TableInfo.TABLE_CHAIN_SUFFIX + "=?",
                prefix01, prefix02, safix
            )
        )
        if (existChain != null && 0 < existChain.size) {
            log.debug("chain exists.")
            return
        }
        if (safix == null) {
            // 文章の終了
            manager.create(
                Chain::class.java, DBParam(
                    TableInfo.TABLE_CHAIN_PREFIX01, prefix01
                ), DBParam(
                    TableInfo.TABLE_CHAIN_PREFIX02, prefix02
                ), DBParam(
                    TableInfo.TABLE_CHAIN_START, start
                )
            )
        } else {
            manager.create(
                Chain::class.java, DBParam(
                    TableInfo.TABLE_CHAIN_PREFIX01, prefix01
                ), DBParam(
                    TableInfo.TABLE_CHAIN_PREFIX02, prefix02
                ), DBParam(
                    TableInfo.TABLE_CHAIN_SUFFIX, safix
                ), DBParam(
                    TableInfo.TABLE_CHAIN_START, start
                )
            )
        }
    }

    /**
     * ゴミ掃除をする.
     */
    @Throws(SQLException::class, IOException::class)
    protected fun cleaning() {
        val cleaningFile = prop.getProperty("com.pokosho.cleaning")
        var file: File? = null
        var filereader: FileReader? = null
        var br: BufferedReader? = null
        try {
            file = File(cleaningFile)
            filereader = FileReader(file)
            br = BufferedReader(filereader)
            var line: String? = null
            while ((line = br.readLine()) != null) {
                val words = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " like '" + line
                                + "%'"
                    )
                )
                log.info(
                    "search " + TableInfo.TABLE_WORD_WORD + " like '"
                            + line + "%'"
                )
                // カンマ区切りにする
                val sb = StringBuffer()
                for (w in words) {
                    sb.append(w.word_ID!!.toString() + ",")
                    log.info(
                        "delete word:" + w.word + " ID:"
                                + w.word_ID
                    )
                }
                if (sb.length == 0)
                    continue
                sb.deleteCharAt(sb.length - 1) // 末尾の 「,」 を取り除く
                manager.delete(
                    *manager.find(
                        Chain::class.java,
                        Query.select().where(
                            TableInfo.TABLE_CHAIN_PREFIX01 + " in (?) OR "
                                    + TableInfo.TABLE_CHAIN_PREFIX02
                                    + " in (?) OR "
                                    + TableInfo.TABLE_CHAIN_SUFFIX
                                    + " in (?)", sb.toString(),
                            sb.toString(), sb.toString()
                        )
                    )
                )
            }
        } finally {
            br?.close()
            filereader?.close()
        }
    }

    /**
     * studyFromLineで新しく学習したtokenを返す。
     *
     * @return 新しく学習したtoken
     */
    protected fun getNewTokens(): List<Token>? {
        return this.newTokens
    }

    /**
     * 単語のIDリストを作成する
     *
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun createWordIDList(startChain: Array<Chain>): List<Int> {
        val chainCountDown = Integer.parseInt(
            prop
                .getProperty("com.pokosho.chain_count_down")
        )
        var loopCount = 0
        val idList = ArrayList<Int>()
        idList.add(startChain[0].prefix01)
        idList.add(startChain[0].prefix02)
        var chain = startChain
        while (true) {
            log.debug("pick next chain which has prefix01 id " + chain[0].suffix!!)
            if (chain[0].suffix == null) {
                break
            }
            // 指定回数連鎖したら終端を探しに行く
            var whereEnd = ""
            if (loopCount > chainCountDown) {
                whereEnd = TableInfo.TABLE_CHAIN_SUFFIX + "=null and "
            }
            var nextChain: Array<Chain>? = manager.find(
                Chain::class.java,
                Query.select().where(
                    whereEnd + TableInfo.TABLE_CHAIN_PREFIX01
                            + "=? order by rand() limit 1",
                    chain[0].suffix
                )
            )
            // 終端が見つからなかった場合は、終端を探すのを諦める
            if (whereEnd.length == 0 && nextChain == null || nextChain!!.size == 0) {
                nextChain = manager.find(
                    Chain::class.java,
                    Query.select().where(
                        TableInfo.TABLE_CHAIN_PREFIX01 + "=? order by rand() limit 1",
                        chain[0].suffix
                    )
                )
            }
            if (nextChain == null || nextChain.size == 0) {
                log.info("no next chain. please study more...")
                break
            }
            idList.add(nextChain[0].prefix01)
            idList.add(nextChain[0].prefix02)
            log.debug("picked chain id " + nextChain[0].chain_ID!!)
            chain = nextChain
            loopCount++
        }
        return idList
    }

    /**
     * 単語のIDリストを作成する
     *
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun createWordIDListEndToStart(
        idList: LinkedList<Int>, startChain: Array<Chain>
    ): LinkedList<Int> {
        var chain = startChain
        while (true) {
            if (chain[0].start!!) {
                break
            }
            // 始まりを探す
            var nextChain: Array<Chain>? = manager.find(
                Chain::class.java,
                Query.select().where(
                    TableInfo.TABLE_CHAIN_SUFFIX + "=? and "
                            + TableInfo.TABLE_CHAIN_START
                            + "=? limit 1", chain[0].prefix01,
                    true
                )
            )
            if (nextChain == null || nextChain.size == 0) {
                // 無かったらランダムピック
                nextChain = manager.find(
                    Chain::class.java,
                    Query.select().where(
                        TableInfo.TABLE_CHAIN_SUFFIX + "=? order by rand() limit 1",
                        chain[0].prefix01
                    )
                )
            }
            if (nextChain == null || nextChain.size == 0) {
                log.info("no next chain. please study more...")
                break
            }
            idList.add(0, nextChain[0].prefix01)
            idList.add(1, nextChain[0].prefix02)
            log.debug("picked chain id " + nextChain[0].chain_ID!!)
            chain = nextChain
        }
        return idList
    }

    /**
     * wordIDのリストから文章を作成する.
     *
     * @param idList
     * @return
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun createWordsFromIDList(idList: List<Int>): String {
        val result = StringBuilder()
        // wordの取得
        for (i in idList.indices) {
            val words = manager.find(
                Word::class.java,
                Query.select().where(
                    TableInfo.TABLE_WORD_WORD_ID + "=?",
                    idList[i]
                )
            )
            if (words[0].word.length <= 0) {
                continue
            }
            val lastChar = words[0].word[words[0].word.length - 1]
            // 半角英語の間にスペースを入れる
            if (result.length != 0
                && TwitterUtils.isAlfabet(lastChar)
                && TwitterUtils
                    .isAlfabet(result[result.length - 1])
            ) {
                result.append(" ")
            }
            // April Fool
            if (APRIL_FOOL) {
                if (!useChikuwa
                    && words[0].pos_ID == Pos.Noun.intValue
                    && Math.random() < 0.3
                ) {
                    useChikuwa = true
                    result.append("チクワ")
                } else {
                    useChikuwa = false
                    result.append(words[0].word)
                }
            } else {
                result.append(words[0].word)
            }
        }
        return result.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AbstractBot::class.java)
        private val APRIL_FOOL = false
        protected val CHAIN_COUNT = 3
        protected var manager: EntityManager
    }
}
