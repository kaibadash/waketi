package com.pokosho.bot.twitter

import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.util.HashSet
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    private val CONTAIN_JPN_PATTERN = Pattern.compile(
        "[ぁ-んァ-ヴ一-龠]+", Pattern.CASE_INSENSITIVE
    )
    private val ALNUM_PATTERN = Pattern.compile(
        "[0-9a-zA-Z:\\-]", Pattern.CASE_INSENSITIVE
    )
    private val ALFABET_PATTERN = Pattern.compile(
        "[a-zA-Z:\\-]", Pattern.CASE_INSENSITIVE
    )
    private val HANGUL_PATTERN = Pattern.compile(
        "[\uAC00-\uD79F]", Pattern.CASE_INSENSITIVE
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
        return if (containsSurrogatePair(tweet)) {
            true
        } else false
    }

    fun containsJPN(tweet: String): Boolean {
        val matcher = CONTAIN_JPN_PATTERN.matcher(tweet)
        return matcher.find()
    }

    fun containsKR(tweet: String): Boolean {
        val matcher = HANGUL_PATTERN.matcher(tweet)
        return matcher.find()
    }

    fun containsSurrogatePair(tweet: String): Boolean {
        for (c in tweet.toCharArray()) {
            if (Character.isLowSurrogate(c) || Character.isHighSurrogate(c)) {
                return true
            }
        }
        return false
    }

    fun isAlnum(c: Char): Boolean {
        val matcher = ALNUM_PATTERN.matcher(Character.toString(c))
        return matcher.find()
    }

    fun isAlfabet(c: Char): Boolean {
        val matcher = ALFABET_PATTERN.matcher(Character.toString(c))
        return matcher.find()
    }

    fun getNotTeachers(notTeachersFile: String): Set<Long> {
        val notTeacher = HashSet<Long>()
        var reader: FileReader? = null
        var br: BufferedReader? = null
        try {
            reader = FileReader(notTeachersFile)
            br = BufferedReader(reader)
            var line: String
            while ((line = br.readLine()) != null) {
                notTeacher.add(java.lang.Long.parseLong(line))
            }
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
        var reader: FileReader? = null
        var br: BufferedReader? = null
        try {
            reader = FileReader(filePath)
            br = BufferedReader(reader)
            var line: String
            while ((line = br.readLine()) != null) {
                stringSet.add(line)
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
