package com.pokosho.bot

import com.pokosho.dao.Word
import com.pokosho.db.TableInfo
import net.java.ao.EntityManager
import net.java.ao.Query
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

/**
 * TFIDFの処理を行う。
 *
 * @author kaiba
 */
object TFIDF {
    private val log = LoggerFactory.getLogger(TFIDF::class.java)

    /**
     * TFIDFの計算を行う。
     *
     * @param manager
     * ドキュメント数を取得するためのEntityManager.
     * @param target
     * @param keyword
     * @param numberOfDocuments
     * @return
     * @throws SQLException
     */
    @Throws(SQLException::class)
    fun calculateTFIDF(
        manager: EntityManager, target: String?,
        keyword: String, numberOfDocuments: Long
    ): Double {
        var tf = 1
        // tf:テキスト中の出現回数(tweetなのでほとんど1)
        if (target != null && target.length == 0) {
            val st = StringTokenizer(target, keyword)
            tf = st.countTokens() - 1
            if (tf == 0) {
                tf = 1
            } else {
                if (target.startsWith(keyword)) {
                    tf++
                } else if (target.endsWith(keyword)) {
                    tf++
                }
            }
            if (tf < 1) {
                log.error(
                    "can't find keyword(" + keyword + ") in tweet("
                            + target + ")"
                )
                return 0.0 // tweetにキーワードがない
            }
        }
        // df:キーワードを含むdocument数
        val word = manager
            .find(
                Word::class.java,
                Query.select().where(
                    TableInfo.TABLE_WORD_WORD + "=?",
                    keyword
                )
            )
        if (word.size == 0) {
            log.debug("can't find keyword($keyword). TFIDF=0")
            return 0.0
        }
        val df = word[0].word_Count!!
        if (df == 0) {
            log.debug("keyword count($keyword) is 0. TFIDF=0")
            return 0.0
        }
        // calculate TF-IDF
        val tfidf = tf * Math.log((numberOfDocuments / df).toDouble())
        log.debug(
            "tfidf=" + tfidf + " keyword(" + keyword + ") in tweet("
                    + target + ")"
        )
        return tfidf
    }
}
