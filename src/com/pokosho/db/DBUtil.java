package com.pokosho.db;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.java.ao.EntityManager;

public class DBUtil {
	private static EntityManager manager;
	private DBUtil() {
	}
	public static EntityManager getEntityManager() {
		if (manager == null) {
			//manager = new EntityManager("jdbc:hsqldb:hsql://localhost", "sa", "");
			manager = new EntityManager("jdbc:mysql://localhost/kaiba", "kaiba", "TODO");

			// ActiveObjects のロガーを取得
			Logger logger = Logger.getLogger("net.java.ao");
			// ログレベルの設定
			logger.setLevel(Level.WARNING);
		}
		return manager;
	}
}

/*

*/
