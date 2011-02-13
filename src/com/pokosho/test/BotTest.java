package com.pokosho.test;

import com.pokosho.PokoshoException;
import com.pokosho.bot.FileStudyBot;
import com.pokosho.bot.twitter.TwitterBot;

public class BotTest {
	private final static String DB_PROP = "./conf/db.properties";
	private final static String BOT_PROP = "./conf/bot.properties";
	private final static String LOG_PROP = "./conf/log.properties";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("file.encoding", "UTF-8");
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
		FileStudyBot b = new FileStudyBot(DB_PROP);
		b.study(filePath);
		System.out.println(b.say());
	}

	@SuppressWarnings("unused")
	private static void startTwitterBot() throws PokoshoException {
		TwitterBot b = new TwitterBot(DB_PROP, BOT_PROP);
		b.study(null);
		b.say();
	}

	//@SuppressWarnings("unused")
	private static void print() throws PokoshoException {
		FileStudyBot b = new FileStudyBot(DB_PROP);
		System.out.println(b.say());
	}
}
