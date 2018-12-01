package com.pokosho.test

import java.lang.reflect.Method

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.pokosho.bot.AbstractBot
import com.pokosho.bot.twitter.TwitterBot
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class TwitterBotTest {
    /**
     * カスタム辞書のテスト
     */
    @Test
    fun testLoadUserDictionary() {
        val method: Method
        try {
            val bot = TwitterBot("./conf/db.properties", "./conf/bot.properties")
            method = AbstractBot::class.java.getDeclaredMethod(
                "loadTokenizer",
                String::class.java
            )
            method.isAccessible = true
            val tokenizer = method
                .invoke(bot, "./src/com/pokosho/test/custom_dic.txt") as Tokenizer
            val tokenList = tokenizer.tokenize("わけちはハイパーちくわ")
            assertEquals("わけち", tokenList.get(0).getSurface())
            assertEquals("は", tokenList.get(1).getSurface())
            assertEquals("ハイパーちくわ", tokenList.get(2).getSurface())
        } catch (e: Exception) {
            e.printStackTrace()
            fail<Any>(e.toString())
        }

    }

    /**
     * neologdを組み込んだkuromojiのテスト
     */
    @Test
    fun testLoadNormalDictionary() {
        val method: Method
        try {
            val bot = TwitterBot("./conf/db.properties", "./conf/bot.properties")
            method = AbstractBot::class.java.getDeclaredMethod(
                "loadTokenizer",
                String::class.java
            )
            method.isAccessible = true
            val tokenizer = method
                .invoke(bot, "") as Tokenizer
            val tokenList = tokenizer.tokenize("関西国際空港は近い")
            assertEquals("関西国際空港", tokenList.get(0).getSurface())
            assertEquals("は", tokenList.get(1).getSurface())
            assertEquals("近い", tokenList.get(2).getSurface())
        } catch (e: Exception) {
            e.printStackTrace()
            fail<Any>(e.toString())
        }

    }
}
