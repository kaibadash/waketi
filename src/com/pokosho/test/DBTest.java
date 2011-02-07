package com.pokosho.test;

import java.sql.SQLException;

import net.java.ao.EntityManager;

import com.pokosho.dao.Word;
import com.pokosho.db.DBUtil;

public class DBTest {

	/**
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		EntityManager mng = DBUtil.getEntityManager();
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
