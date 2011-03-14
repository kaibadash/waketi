package com.pokosho.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokosho.PokoshoException;
import com.pokosho.bot.FileStudyBot;
import com.pokosho.bot.twitter.TwitterBot;
import com.pokosho.util.StringUtils;

public class BotTest {
	private static Logger log = LoggerFactory.getLogger(BotTest.class);
	private final static String DB_PROP = "./conf/db.properties";
	private final static String BOT_PROP = "./conf/bot.properties";
	private final static String LOG_PROP = "./conf/log.properties";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("file.encoding", StringUtils.ENCODE_STRING);
			System.setProperty("sen.home","./sen-1.2.2.1");
			System.setProperty("java.util.logging.config.file", LOG_PROP);
			System.setProperty("twitter4j.loggerFactory", "twitter4j.internal.logging.NullLoggerFactory");
			//studyFromFile();
			//print();
			startTwitterBot();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private static void studyFromFile() throws PokoshoException {
		String filePath = "./test/ruizu.txt";
		FileStudyBot b = new FileStudyBot(DB_PROP, BOT_PROP);
		b.study(filePath);
		log.info(b.say());
	}

//	@SuppressWarnings("unused")
	private static void startTwitterBot() throws PokoshoException {
		TwitterBot b = new TwitterBot(DB_PROP, BOT_PROP);
		b.study(null);
		b.say();
	}

	@SuppressWarnings("unused")
	private static void print() throws PokoshoException {
		FileStudyBot b = new FileStudyBot(DB_PROP, BOT_PROP);
		System.out.println(b.say());
	}
}
