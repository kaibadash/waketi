package com.pokosho.bot;

import java.sql.SQLException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.java.ao.EntityManager;
import net.java.ao.Query;

import com.pokosho.dao.Word;
import com.pokosho.db.TableInfo;

/**
 * TFIDFの処理を行う。
 * 
 * @author kaiba
 *
 */
public class TFIDF {
	private static Logger log = LoggerFactory.getLogger(TFIDF.class);

	/**
	 * TFIDFの計算を行う。
	 * 
	 * @param manager
	 *            ドキュメント数を取得するためのEntityManager.
	 * @param target
	 * @param keyword
	 * @param numberOfDocuments
	 * @return
	 * @throws SQLException
	 */
	public static double calculateTFIDF(EntityManager manager, String target,
			String keyword, long numberOfDocuments) throws SQLException {
		int tf = 1;
		// tf:テキスト中の出現回数(tweetなのでほとんど1)
		if (target != null && target.length() == 0) {
			StringTokenizer st = new StringTokenizer(target, keyword);
			tf = st.countTokens() - 1;
			if (tf == 0) {
				tf = 1;
			} else {
				if (target.startsWith(keyword)) {
					tf++;
				} else if (target.endsWith(keyword)) {
					tf++;
				}
			}
			if (tf < 1) {
				log.error("can't find keyword(" + keyword + ") in tweet("
						+ target + ")");
				return 0; // tweetにキーワードがない
			}
		}
		// df:キーワードを含むdocument数
		Word[] word = manager
				.find(Word.class,
						Query.select().where(TableInfo.TABLE_WORD_WORD + "=?",
								keyword));
		if (word.length == 0) {
			log.debug("can't find keyword(" + keyword + "). TFIDF=0");
			return 0;
		}
		int df = word[0].getWord_Count();
		if (df == 0) {
			log.debug("keyword count(" + keyword + ") is 0. TFIDF=0");
			return 0;
		}
		// calculate TF-IDF
		double tfidf = tf * Math.log(numberOfDocuments / df);
		log.debug("tfidf=" + tfidf + " keyword(" + keyword + ") in tweet("
				+ target + ")");
		return tfidf;
	}
}
