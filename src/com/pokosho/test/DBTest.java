package com.pokosho.test;

import java.sql.SQLException;

import net.java.ao.EntityManager;

import com.pokosho.PokoshoException;
import com.pokosho.dao.Word;
import com.pokosho.db.DBUtil;

public class DBTest {
	private final static String DB_PROP = "../conf/db.properties";

	/**
	 * @param args
	 * @throws PokoshoException
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws PokoshoException {
		EntityManager mng = DBUtil.getEntityManager(DB_PROP);
		try {
			mng.migrate(Word.class);
			Word word = mng.create(Word.class);
			int rand = (int)(Math.random() * 100);
			word.setWord("test" + rand);
			word.save();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}
