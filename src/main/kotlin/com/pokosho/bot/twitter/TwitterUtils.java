package com.pokosho.bot.twitter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Twitter関連Utilクラス
 */
public class TwitterUtils {
	private static Logger log = LoggerFactory.getLogger(TwitterUtils.class);
	private static final Pattern HASHTAG_PATTERN = Pattern.compile(
			"#[a-z0-9_]*", Pattern.CASE_INSENSITIVE);
	private static final Pattern URL_PATTERN = Pattern.compile(
			"(http://|https://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern MENTION_PATTERN = Pattern.compile(
			"@[a-z0-9_:]*", Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTAIN_JPN_PATTERN = Pattern.compile(
			"[ぁ-んァ-ヴ一-龠]+", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALNUM_PATTERN = Pattern.compile(
			"[0-9a-zA-Z:\\-]", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALFABET_PATTERN = Pattern.compile(
			"[a-zA-Z:\\-]", Pattern.CASE_INSENSITIVE);
	private static final Pattern HANGUL_PATTERN = Pattern.compile(
			"[\uAC00-\uD79F]", Pattern.CASE_INSENSITIVE);
	private static final String FOUR_SQ_URL = "http://4sq.com/";
	private static final String RT_STR = "RT";
	private static final String QT_STR = "QT";

	public static String removeHashTags(String str) {
		Matcher matcher = HASHTAG_PATTERN.matcher(str);
		return matcher.replaceAll("").trim();
	}

	public static String removeUrl(String str) {
		Matcher matcher = URL_PATTERN.matcher(str);
		return matcher.replaceAll("").trim();
	}

	public static String removeMention(String str) {
		Matcher matcher = MENTION_PATTERN.matcher(str);
		return matcher.replaceAll("").trim();
	}

	public static String removeRTString(String str) {
		String res = str.replaceAll(RT_STR, "");
		return res.replaceAll(QT_STR, "").trim();
	}

	public static boolean isSpamTweet(String tweet) {
		if (tweet.contains(FOUR_SQ_URL))
			return true;
		if (containsKR(tweet)) {
			return true;
		}
		if (containsSurrogatePair(tweet)) {
			return true;
		}
		return false;
	}

	public static boolean containsJPN(String tweet) {
		Matcher matcher = CONTAIN_JPN_PATTERN.matcher(tweet);
		return matcher.find();
	}

	public static boolean containsKR(String tweet) {
		Matcher matcher = HANGUL_PATTERN.matcher(tweet);
		return matcher.find();
	}
	
	public static boolean containsSurrogatePair(String tweet) {
		for (char c : tweet.toCharArray()) {
			if (Character.isLowSurrogate(c) || Character.isHighSurrogate(c)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAlnum(char c) {
		Matcher matcher = ALNUM_PATTERN.matcher(Character.toString(c));
		return matcher.find();
	}

	public static boolean isAlfabet(char c) {
		Matcher matcher = ALFABET_PATTERN.matcher(Character.toString(c));
		return matcher.find();
	}

	public static Set<Long> getNotTeachers(String notTeachersFile) {
		Set<Long> notTeacher = new HashSet<Long>();
		FileReader reader = null;
		BufferedReader br = null;
		try {
			reader = new FileReader(notTeachersFile);
			br = new BufferedReader(reader);
			String line;
			while ((line = br.readLine()) != null) {
				notTeacher.add(Long.parseLong(line));
			}
		} catch (NumberFormatException e) {
			log.error("getNotTeachers error", e);
		} catch (FileNotFoundException e) {
			log.debug("not set not teacher", e);
		} catch (IOException e) {
			log.error("getNotTeachers error", e);
		} finally {
			try {
				if (br != null) {
					br.close();
				}
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				log.error("close error", e);
			}
		}
		return notTeacher;
	}

	public static Set<String> getStringSet(String filePath) {
		Set<String> stringSet = new HashSet<String>();
		FileReader reader = null;
		BufferedReader br = null;
		try {
			reader = new FileReader(filePath);
			br = new BufferedReader(reader);
			String line;
			while ((line = br.readLine()) != null) {
				stringSet.add(line);
			}
		} catch (NumberFormatException e) {
			log.error("format error", e);
		} catch (FileNotFoundException e) {
			log.debug("file not found", e);
		} catch (IOException e) {
			log.error("IOException error", e);
		} finally {
			try {
				if (br != null) {
					br.close();
				}
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				log.error("close error", e);
			}
		}
		return stringSet;
	}
}
