package com.pokosho.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokosho.db.Pos;

public class StringUtils {
	private static Logger log = LoggerFactory.getLogger(StringUtils.class);
	public static String ENCODE_STRING = "UTF-8";
	private final static String NOUN = "名詞";
	private final static String VERV = "動詞";
	private final static String ADJECTIVE = "形容詞";
	private final static String ADVERB = "副詞";
	private final static String PRONOUN = "代名詞";
	private final static String PREPOSITION = "前置詞";
	private final static String CONJUNCTION = "接続詞";
	private final static String INTERJECTION = "感動詞";
	private final static String RENTAI = "連体詞";
	private final static String JOSHI = "助詞";
	private final static String UNKNOWN = "未知語"; /*名詞扱い*/
	//private final static String OTHER = "記号";

	/**
	 * 文字列をシンプルにする.
	 * URLを除いたり、全角半角統一、「」削除を行う.
	 * @param str
	 * @return
	 */
	public static String simplize(String str) {
		// URLを削除
		String res = str.replaceAll("^(https?|ftp)(:\\/\\/[-_.!~*\\'()a-zA-Z0-9;\\/?:\\@&=+\\$,%#]+)", "");
		res = res.replaceAll("@[a-zA-Z0-9_]+", "");
		res = res.replaceAll("[「【(（『].*[」】）』¥¥(¥¥)¥¥[¥¥]]", "");
		res = res.replaceAll("\"", "");
		return res;
	}

	public static String simplizeForReply(String str) {
		String res = StringUtils.simplize(str);
		res = res.replaceAll("[¥¥!！¥¥?？¥¥.。¥¥-]", "");
		return res;
	}

	public static Pos toPos(String posStr) {
		String p = posStr.split("-")[0];
		log.debug("toPos:" + p + " detail:" + posStr);
		if (p == null || p.length() == 0 || p.startsWith(UNKNOWN)) {
			/* 未知語は名詞扱い */
			return Pos.Noun;
		}
		if (p.startsWith(NOUN)) {
			return Pos.Noun;
		}
		if (p.startsWith(VERV)) {
			return Pos.Verv;
		}
		if (p.startsWith(INTERJECTION)) {
			return Pos.Interjection;
		}
		if (p.startsWith(ADJECTIVE)) {
			return Pos.Adjective;
		}
		if (p.startsWith(ADVERB)) {
			return Pos.Adverb;
		}
		if (p.startsWith(PRONOUN)) {
			return Pos.Preposition;
		}
		if (p.startsWith(CONJUNCTION)) {
			return Pos.Conjunction;
		}
		if (p.startsWith(PREPOSITION)) {
			return Pos.Preposition;
		}
		if (p.startsWith(RENTAI)) {
			return Pos.Rentai;
		}
		if (p.startsWith(JOSHI)) {
			return Pos.Joshi;
		}
		return Pos.Other;
	}
}

