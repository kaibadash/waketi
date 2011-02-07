/**
 *
 */
package com.pokosho.morph;

import java.io.IOException;
import java.sql.SQLException;

import net.java.ao.EntityManager;
import net.java.sen.StringTagger;
import net.java.sen.Token;

import com.pokosho.PokoshoException;
import com.pokosho.dao.Word;
import com.pokosho.db.DBUtil;

/**
 *
 * @author kaiba
 *
 */
public class MorphUtil {
	/**
	 * パースしてDBに挿入
	 * @param str
	 */
	public static void parse(String str) throws PokoshoException {
		try {
			StringTagger tagger = StringTagger.getInstance();
			Token[] token = tagger.analyze(str);
			EntityManager mng = DBUtil.getEntityManager();
			for (Token t : token) {
				Word w = mng.create(Word.class);
				w.setWord(t.getSurface());
				w.save();

				// get id
				//Word[] savedWords = mng.find(Word.class, Query.select().where("WORD = ?", t.getSurface()));
			}
		} catch (IOException e) {
			throw new PokoshoException(e);
		} catch (SQLException e) {
			throw new PokoshoException(e);
		}
	}
}
