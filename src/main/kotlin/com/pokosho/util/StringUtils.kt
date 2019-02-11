package com.pokosho.util

import com.pokosho.db.Pos
import java.util.regex.Pattern

object StringUtils {
    var ENCODE_STRING = "UTF-8"
    private val ALFABET_PATTERN = Pattern.compile(
        "[a-zA-Z:\\-]", Pattern.CASE_INSENSITIVE
    )
    private val CONTAIN_JPN_PATTERN = Pattern.compile(
        "[ぁ-んァ-ヴ一-龠]+", Pattern.CASE_INSENSITIVE
    )

    private val HANGUL_PATTERN = Pattern.compile(
        "[\uAC00-\uD79F]", Pattern.CASE_INSENSITIVE
    )
    private val NOUN = "名詞"
    private val VERV = "動詞"
    private val ADJECTIVE = "形容詞"
    private val ADVERB = "副詞"
    private val PRONOUN = "代名詞"
    private val PREPOSITION = "前置詞"
    private val CONJUNCTION = "接続詞"
    private val INTERJECTION = "感動詞"
    private val RENTAI = "連体詞"
    private val JOSHI = "助詞"
    private val UNKNOWN = "未知語" /* 名詞扱い */

    /**
     * 文字列をシンプルにする. URLを除いたり、全角半角統一、「」削除を行う.
     *
     * @param str
     * @return
     */
    fun simplify(str: String): String {
        // URLを削除
        var res = str
            .replace("^(https?|ftp)(:\\/\\/[-_.!~*\\'()a-zA-Z0-9;\\/?:\\@&=+\\$,%#]+)".toRegex(), "")
        res = res.replace("@[a-zA-Z0-9_]+".toRegex(), "")
        res = res.replace("[「【(（『].*[」】）』¥¥(¥¥)¥¥[¥¥]]".toRegex(), "")
        res = res.replace("\"".toRegex(), "")
        res = res.replace("[\uE000-\uF8FF]".toRegex(), "。") // 携帯の絵文字
        return res
    }

    fun simplifyForReply(str: String): String {
        return StringUtils.simplify(str).replace("[¥¥!！¥¥?？¥¥.。¥¥-]".toRegex(), "")
    }

    fun toPos(posStr: String): Pos {
        val p = posStr.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        // log.debug("posStr:" + posStr + " toPos:" + p + " detail:" + posStr);
        if (p.length == 0 || p.startsWith(UNKNOWN)) {
            /* 未知語は名詞扱い */
            return Pos.Noun
        }
        if (p.startsWith(NOUN)) {
            return Pos.Noun
        }
        if (p.startsWith(VERV)) {
            return Pos.Verv
        }
        if (p.startsWith(INTERJECTION)) {
            return Pos.Interjection
        }
        if (p.startsWith(ADJECTIVE)) {
            return Pos.Adjective
        }
        if (p.startsWith(ADVERB)) {
            return Pos.Adverb
        }
        if (p.startsWith(PRONOUN)) {
            return Pos.Preposition
        }
        if (p.startsWith(CONJUNCTION)) {
            return Pos.Conjunction
        }
        if (p.startsWith(PREPOSITION)) {
            return Pos.Preposition
        }
        if (p.startsWith(RENTAI)) {
            return Pos.Rentai
        }
        return if (p.startsWith(JOSHI)) {
            Pos.Joshi
        } else Pos.Other
    }

    fun isAlfabet(c: Char): Boolean {
        val matcher = ALFABET_PATTERN.matcher(Character.toString(c))
        return matcher.find()
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
}
