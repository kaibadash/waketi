package com.pokosho.test

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.pokosho.PokoshoException
import com.pokosho.bot.FileStudyBot
import com.pokosho.bot.twitter.TwitterBot
import com.pokosho.util.StringUtils

object BotTest {
    private val log = LoggerFactory.getLogger(BotTest::class.java)
    private val DB_PROP = "./conf/db.properties"
    private val BOT_PROP = "./conf/bot.properties"
    private val LOG_PROP = "./conf/log.properties"
    /**
     * @param args
     */
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            System.setProperty("file.encoding", StringUtils.ENCODE_STRING)
            System.setProperty("sen.home", "./sen-1.2.2.1")
            System.setProperty("java.util.logging.config.file", LOG_PROP)
            System.setProperty("twitter4j.loggerFactory", "twitter4j.internal.logging.NullLoggerFactory")
            //studyFromFile();
            //print();
            startTwitterBot()

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    @Throws(PokoshoException::class)
    private fun studyFromFile() {
        val filePath = "./test/ruizu.txt"
        val b = FileStudyBot(DB_PROP, BOT_PROP)
        b.study(filePath)
        log.info(b.say())
    }

    //	@SuppressWarnings("unused")
    @Throws(PokoshoException::class)
    private fun startTwitterBot() {
        val b = TwitterBot(DB_PROP, BOT_PROP)
        //b.study(null);
        //b.say();
        //b.say("東京タワー登りたいの？");
        b.reply()
    }

    @Throws(PokoshoException::class)
    private fun print() {
        val b = FileStudyBot(DB_PROP, BOT_PROP)
        println(b.say())
    }
}
