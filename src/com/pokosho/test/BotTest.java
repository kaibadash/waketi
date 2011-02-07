package com.pokosho.test;

import com.pokosho.PokoshoException;
import com.pokosho.bot.FileStudyBot;
import com.pokosho.bot.twitter.TwitterBot;

public class BotTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("sen.home","C:/usr/local/sen-1.2.2.1");
			//studyFromFile();
			//tweet();
			print();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void studyFromFile() throws PokoshoException {
		String filePath = "./ruizu.txt";
		FileStudyBot b = new FileStudyBot();
		b.study(filePath);
		System.out.println(b.say());
	}

	private static void tweet() throws PokoshoException {
		TwitterBot b = new TwitterBot();
		b.say();
	}

	private static void print() throws PokoshoException {
		FileStudyBot b = new FileStudyBot();
		System.out.println(b.say());
	}


}
