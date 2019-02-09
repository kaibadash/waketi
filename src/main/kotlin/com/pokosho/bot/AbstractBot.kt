package com.pokosho.bot

import com.pokosho.PokoshoException
import com.pokosho.bot.twitter.TwitterUtils
import com.pokosho.dao.Chain
import com.pokosho.dao.Word
import com.pokosho.db.DBUtil
import com.pokosho.db.Pos
import com.pokosho.db.TableInfo
import com.pokosho.util.StrRep
import com.pokosho.util.StringUtils
import net.java.ao.EntityManager
import net.java.ao.Query
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.slf4j.LoggerFactory
import java.io.*
import java.sql.SQLException
import java.util.*


abstract class AbstractBot @Throws(PokoshoException::class)
constructor(dbPropPath: String, botPropPath: String) {
    private val tokenizer: JapaneseTokenizer
    private val strRep: StrRep
    private val prop: Properties
    private val manager: EntityManager
    private val log = LoggerFactory.getLogger(AbstractBot::class.java)
    private val CHAIN_COUNT = 3

    init {
        prop = Properties()
        log.debug("dbPropPath:$dbPropPath")
        log.debug("botPropPath:$botPropPath")
        try {
            prop.load(FileInputStream(botPropPath))
            strRep = StrRep(prop.getProperty("com.pokosho.repstr"))
            this.manager = DBUtil.getEntityManager(dbPropPath)
        } catch (e1: FileNotFoundException) {
            log.error("FileNotFoundException", e1)
            throw PokoshoException(e1)
        } catch (e1: IOException) {
            log.error("IOException", e1)
            throw PokoshoException(e1)
        } catch (e: IllegalArgumentException) {
            log.error("system error", e)
            throw PokoshoException(e)
        }

        this.tokenizer = JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL)
    }

    /**
     * 学習する.
     *
     * @param file
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    abstract fun study(str: String?)

    /**
     * 発言を返す.
     *
     * @return 発言.
     * @throws PokoshoException
     */
    @Throws(PokoshoException::class)
    open fun say(): String? {
        var result: String?
        try {
            val chain = manager.find(
                Chain::class.java,
                Query.select()
                    .where(TableInfo.TABLE_CHAIN_START + " = ?", true)
                    .order("rand()").limit(1)
            )
            if (chain == null || chain.size == 0) {
                log.info("Bot knows no words.")
                return null
            }
            // 終了まで文章を組み立てる
            val idList = LinkedList(createWordIDList(chain))
            result = createWordsFromIDList(idList)
            result = strRep.rep(result)
        } catch (e: SQLException) {
            log.error(e.message, e)
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
        var targetFrom = from
        var maxTFIDF = 0.0
        var keyword: Word? = null

        try {
            log.debug("reply base:$targetFrom")
            targetFrom = StringUtils.simplizeForReply(targetFrom)
            log.debug("simplizeForReply:$targetFrom")
            tokenizer.setReader(StringReader(targetFrom))
            tokenizer.reset()

            while (tokenizer.incrementToken()) {
                val charAttr = tokenizer.addAttribute(CharTermAttribute::class.java)
                val posAttr = tokenizer.addAttribute(PartOfSpeechAttribute::class.java)

                val word = this.manager.create(Word::class.java)
                word.word = charAttr.toString()
                word.pos_ID = StringUtils.toPos(posAttr.partOfSpeech).intValue
                log.debug("surface:${word.word} features:${word.word}")

                // FIXME: wordにenumを保持できないか? intValueやめたい。
                if (word.pos_ID == Pos.Noun.intValue) {
                    val tdidf = TFIDF.calculateTFIDF(
                        manager, targetFrom,
                        word.word, numberOfDocuments.toLong()
                    )
                    if (maxTFIDF < tdidf) {
                        maxTFIDF = tdidf
                        keyword = word
                    }
                }
            }

            // 最大コストの単語で始まっているか調べて、始まっていたら使う
            var words: Array<Word>? = null
            if (0 < maxTFIDF) {
                words = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " = ?",
                        keyword!!.word
                    )
                )
            }

            // word found, start creating chain
            var chain: Array<Chain>? = manager.find(
                Chain::class.java,
                Query.select().where(
                    TableInfo.TABLE_CHAIN_START + " = ? and "
                            + TableInfo.TABLE_CHAIN_PREFIX01
                            + " = ?  order by rand()", true,
                    words!![0].word_ID
                ).limit(1)
            )
            var startWithMaxCountWord = (chain != null && chain.size > 0)
            chain = manager.find(
                Chain::class.java,
                Query.select().where(
                    TableInfo.TABLE_CHAIN_PREFIX01 + " = ?  order by rand()", words[0].word_ID
                ).limit(1)
            )
            // 終了まで文章を組み立てる
            var idList = LinkedList(createWordIDList(chain))
            if (!startWithMaxCountWord) {
                // 先頭まで組み立てる
                idList = createWordIDListEndToStart(idList, chain)
            }
            return strRep.rep(createWordsFromIDList(idList))
        } catch (e: SQLException) {
            throw PokoshoException(e)
        }
    }

    @Throws(IOException::class, SQLException::class)
    protected fun studyFromLine(str: String?) {
        // スパム判定
        if (str == null || str.isEmpty()) {
            return
        }
        var target = str
        if (!TwitterUtils.containsJPN(target)) {
            return
        }
        // TODO:AbstractBoxにTweetの処理が来るのはおかしい… TwitterBotでやるべき
        if (TwitterUtils.isSpamTweet(target)) {
            return
        }
        target = StringUtils.simplize(target)

        tokenizer.setReader(StringReader(target))
        tokenizer.reset()
        val chainTmp = arrayOfNulls<Int>(CHAIN_COUNT)

        while (tokenizer.incrementToken()) {
            val word = tokenizer.addAttribute(CharTermAttribute::class.java).toString()
            val posAttr = tokenizer.addAttribute(PartOfSpeechAttribute::class.java)
            val posID = StringUtils.toPos(posAttr.partOfSpeech).intValue

            if (word.trim().isEmpty()) continue
            var existWord = manager.find(
                Word::class.java,
                Query.select().where(TableInfo.TABLE_WORD_WORD + " = ?", word)
            )
            if (existWord == null || existWord.size == 0) {
                val newWord = manager.create(
                    Word::class.java, mapOf(
                        "pos_ID" to posID,
                        "word" to word,
                        "word_Count" to 1,
                        "time" to (System.currentTimeMillis() / 1000).toInt()
                    )
                )
                newWord.save()

                // IDを取得
                existWord = manager.find(
                    Word::class.java,
                    Query.select().where(TableInfo.TABLE_WORD_WORD + " = ?", word)
                )
                // createで作っている時点でIDは分かるので無駄…
            } else {
                existWord[0].word_Count = existWord[0].word_Count!! + 1
                existWord[0].time = (System.currentTimeMillis() / 1000).toInt()
                existWord[0].save()
            }
            // FIXME: 泥臭い。3階のマルコフ連鎖にしか対応できてない。
            if (chainTmp[0] == null) {
                chainTmp[0] = existWord!![0].word_ID
                continue
            }
            if (chainTmp[1] == null) {
                chainTmp[1] = existWord!![0].word_ID
                continue
            }
            if (chainTmp[2] == null) {
                chainTmp[2] = existWord!![0].word_ID
                createChain(chainTmp[0], chainTmp[1], chainTmp[2], true)
                continue
            }
            // swap
            chainTmp[0] = chainTmp[1]
            chainTmp[1] = chainTmp[2]
            chainTmp[2] = existWord!![0].word_ID
            createChain(chainTmp[0], chainTmp[1], chainTmp[2], false)
        }
        createChain(chainTmp[1], chainTmp[2], null, false)
        tokenizer.close()
    }

    /**
     * ゴミ掃除をする.
     */
    protected fun cleaning() {
        val cleaningFile = prop.getProperty("com.pokosho.cleaning")
        val file: File?
        var reader: FileReader? = null
        var br: BufferedReader? = null
        try {
            file = File(cleaningFile)
            reader = FileReader(file)
            br = BufferedReader(reader)
            var line: String?

            do {
                line = br.readLine()
                val words = manager.find(
                    Word::class.java,
                    Query.select().where(
                        TableInfo.TABLE_WORD_WORD + " like '" + line
                                + "%'"
                    )
                )
                // カンマ区切りにする
                val sb = StringBuffer()
                for (w in words) {
                    sb.append(w.word_ID!!.toString() + ",")
                }
                if (sb.length == 0) {
                    continue
                }
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
            } while (line != null)
        } finally {
            br?.close()
            reader?.close()
        }
    }

    @Throws(SQLException::class)
    private fun createChain(
        prefix01: Int?, prefix02: Int?,
        suffix: Int?, start: Boolean?
    ) {
        log.debug(
            String.format(
                "createChain:%d,%d,%d", prefix01, prefix02,
                suffix
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
                prefix01, prefix02, suffix
            )
        )
        if (existChain != null && 0 < existChain.size) {
            log.debug("chain exists.")
            return
        }
        if (suffix == null) {
            // 文章の終了
            manager.create(
                Chain::class.java, mapOf(
                    TableInfo.TABLE_CHAIN_PREFIX01 to prefix01,
                    TableInfo.TABLE_CHAIN_PREFIX02 to prefix02,
                    TableInfo.TABLE_CHAIN_START to start
                )
            )
        } else {
            manager.create(
                Chain::class.java, mapOf(
                    TableInfo.TABLE_CHAIN_PREFIX01 to prefix01,
                    TableInfo.TABLE_CHAIN_PREFIX02 to prefix02,
                    TableInfo.TABLE_CHAIN_SUFFIX to suffix,
                    TableInfo.TABLE_CHAIN_START to start
                )
            )
        }
    }

    /**
     * 単語のIDリストを作成する
     *
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun createWordIDList(startChain: Array<Chain>): List<Int?> {
        val chainCountDown = Integer.parseInt(
            prop
                .getProperty("com.pokosho.chain_count_down")
        )
        var loopCount = 0
        val idList = ArrayList<Int?>()
        idList.add(startChain[0].prefix01)
        idList.add(startChain[0].prefix02)
        var chain = startChain
        while (true) {
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
                            + "=? order by rand()",
                    chain[0].suffix
                ).limit(1)
            )
            // 終端が見つからなかった場合は、終端を探すのを諦める
            if (whereEnd.length == 0 && nextChain == null || nextChain!!.size == 0) {
                nextChain = manager.find(
                    Chain::class.java,
                    Query.select().where(
                        TableInfo.TABLE_CHAIN_PREFIX01 + "=? order by rand()",
                        chain[0].suffix
                    ).limit(1)
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
    private fun createWordIDListEndToStart(idList: LinkedList<Int?>, startChain: Array<Chain>): LinkedList<Int?> {
        var chain = startChain
        while (true) {
            if (chain[0].start!!) {
                break
            }
            // 始まりを探す
            var nextChain: Array<Chain> = manager.find(
                Chain::class.java,
                Query.select().where(
                    TableInfo.TABLE_CHAIN_SUFFIX + "=? and "
                            + TableInfo.TABLE_CHAIN_START
                            + "=?", chain[0].prefix01, true
                ).limit(1)
            )
            if (nextChain.isEmpty()) {
                nextChain = manager.find(
                    Chain::class.java,
                    Query.select().where(
                        TableInfo.TABLE_CHAIN_SUFFIX + "=? order by rand()",
                        chain[0].prefix01
                    ).limit(1)
                )
            }
            if (nextChain.isEmpty()) {
                log.info("There is no next chain. please study more...")
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
    private fun createWordsFromIDList(idList: List<Int?>): String {
        val result = StringBuilder()
        for (id in idList) {
            if (id == null) continue // FIXME: List<Int>にできるはずだ
            val word = manager.find(
                Word::class.java,
                Query.select().where(TableInfo.TABLE_WORD_WORD_ID + "=?", id)
            ).first()
            val lastChar = word.word.last()
            // 半角英語の間にスペースを入れる
            if (result.length != 0
                && TwitterUtils.isAlfabet(lastChar)
                && TwitterUtils.isAlfabet(result[result.length - 1])
            ) {
                result.append(" ")
            }
            result.append(word.word)
        }
        return result.toString()
    }
}
