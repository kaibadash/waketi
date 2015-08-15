package com.pokosho.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.pokosho.bot.AbstractBot;
import com.pokosho.bot.twitter.TwitterBot;

public class TwitterBotTest {
	/**
	 * カスタム辞書のテスト
	 */
	@Test
	public void testLoadUserDictionary() {
		Method method;
		try {
			TwitterBot bot = new TwitterBot("./conf/db.properties", "./conf/bot.properties");
			method = AbstractBot.class.getDeclaredMethod("loadTokenizer",
					String.class);
			method.setAccessible(true);
			Tokenizer tokenizer = (Tokenizer) method
					.invoke(bot, "./src/com/pokosho/test/custom_dic.txt");
			List<Token> tokenList = tokenizer.tokenize("わけちはハイパーちくわ");
			assertEquals("わけち", tokenList.get(0).getSurfaceForm());
			assertEquals("は", tokenList.get(1).getSurfaceForm());
			assertEquals("ハイパーちくわ", tokenList.get(2).getSurfaceForm());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}
	/**
	 * neologdを組み込んだkuromojiのテスト
	 */
	@Test
	public void testLoadNormalDictionary() {
		Method method;
		try {
			TwitterBot bot = new TwitterBot("./conf/db.properties", "./conf/bot.properties");
			method = AbstractBot.class.getDeclaredMethod("loadTokenizer",
					String.class);
			method.setAccessible(true);
			Tokenizer tokenizer = (Tokenizer) method
					.invoke(bot, "");
			List<Token> tokenList = tokenizer.tokenize("関西国際空港は近い");
			assertEquals("関西国際空港", tokenList.get(0).getSurfaceForm());
			assertEquals("は", tokenList.get(1).getSurfaceForm());
			assertEquals("近い", tokenList.get(2).getSurfaceForm());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}
}
