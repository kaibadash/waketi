package com.pokosho.bot.twitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterUtils {
	private static final Pattern HASHTAG_PATTERN = Pattern.compile("#[a-z0-9_]*",Pattern.CASE_INSENSITIVE);
	private static final Pattern URL_PATTERN = Pattern.compile("(http://|https://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+", Pattern.CASE_INSENSITIVE);
	private static final Pattern MENTION_PATTERN = Pattern.compile("@[a-z0-9_:]*", Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTAIN_JPN_PATTERN = Pattern.compile("@[ぁ-んァ-ヴ一-龠]+", Pattern.CASE_INSENSITIVE);
	private static final String FOUR_SQ_URL = "http://4sq.com/";
	private static final String RT_STR = "RT";
	private static final String QT_STR = "QT";


	public static String removeHashTags(String str) {
		Matcher matcher = HASHTAG_PATTERN.matcher(str);
		return matcher.replaceAll("");
	}

	public static String removeUrl(String str) {
		Matcher matcher = URL_PATTERN.matcher(str);
		return matcher.replaceAll("");
	}

	public static String removeMention(String str) {
		Matcher matcher = MENTION_PATTERN.matcher(str);
		return matcher.replaceAll("");
	}

	public static String removeRTString(String str) {
		String res = str.replaceAll(RT_STR, "");
		res = res.replaceAll(QT_STR, "");
		return res;
	}

	public static boolean isSpamTweet(String tweet) {
		if (tweet.contains(FOUR_SQ_URL)) return true;
		return false;
	}

	public static boolean containsJPN(String tweet) {
		Matcher matcher = CONTAIN_JPN_PATTERN.matcher(tweet);
		return matcher.find();
	}
}
