package com.pokosho.bot.twitter

import com.pokosho.util.StringUtils.containsKR
import com.pokosho.util.StringUtils.containsSurrogatePair
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*
import java.util.regex.Pattern

/**
 * Twitter関連Utilクラス
 */
object TwitterUtils {
    private val log = LoggerFactory.getLogger(TwitterUtils::class.java)
    private val HASHTAG_PATTERN = Pattern.compile(
        "#[a-z0-9_]*", Pattern.CASE_INSENSITIVE
    )
    private val URL_PATTERN = Pattern.compile(
        "(http://|https://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+",
        Pattern.CASE_INSENSITIVE
    )
    private val MENTION_PATTERN = Pattern.compile(
        "@[a-z0-9_:]*", Pattern.CASE_INSENSITIVE
    )

    private val FOUR_SQ_URL = "http://4sq.com/"
    private val RT_STR = "RT"
    private val QT_STR = "QT"

    fun removeHashTags(str: String): String {
        val matcher = HASHTAG_PATTERN.matcher(str)
        return matcher.replaceAll("").trim { it <= ' ' }
    }

    fun removeUrl(str: String): String {
        val matcher = URL_PATTERN.matcher(str)
        return matcher.replaceAll("").trim { it <= ' ' }
    }

    fun removeMention(str: String): String {
        val matcher = MENTION_PATTERN.matcher(str)
        return matcher.replaceAll("").trim { it <= ' ' }
    }

    fun removeRTString(str: String): String {
        val res = str.replace(RT_STR.toRegex(), "")
        return res.replace(QT_STR.toRegex(), "").trim { it <= ' ' }
    }

    fun isSpamTweet(tweet: String): Boolean {
        if (tweet.contains(FOUR_SQ_URL))
            return true
        if (containsKR(tweet)) {
            return true
        }
        return containsSurrogatePair(tweet)
    }

    fun getNotTeachers(notTeachersFile: String): Set<Long> {
        val notTeacher = HashSet<Long>()
        var reader: FileReader? = null
        var br: BufferedReader? = null
        try {
            reader = FileReader(notTeachersFile)
            br = BufferedReader(reader)
            var line: String?
            do {
                line = br.readLine()
                if (line == null) break
                notTeacher.add(java.lang.Long.parseLong(line))
            } while (line != null)
        } catch (e: NumberFormatException) {
            log.error("getNotTeachers error", e)
        } catch (e: FileNotFoundException) {
            log.debug("not set not teacher", e)
        } catch (e: IOException) {
            log.error("getNotTeachers error", e)
        } finally {
            try {
                br?.close()
                reader?.close()
            } catch (e: IOException) {
                log.error("close error", e)
            }

        }
        return notTeacher
    }

    fun getStringSet(filePath: String): Set<String> {
        val stringSet = HashSet<String>()
        val reader: FileReader? = null
        val br: BufferedReader? = null
        try {
            File(filePath).readText().split("\n").filter { it.isNotEmpty() }.forEach {
                stringSet.add(it)
            }
        } catch (e: NumberFormatException) {
            log.error("format error", e)
        } catch (e: FileNotFoundException) {
            log.debug("file not found", e)
        } catch (e: IOException) {
            log.error("IOException error", e)
        } finally {
            try {
                br?.close()
                reader?.close()
            } catch (e: IOException) {
                log.error("close error", e)
            }

        }
        return stringSet
    }
}
