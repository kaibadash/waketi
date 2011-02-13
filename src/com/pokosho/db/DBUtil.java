package com.pokosho.db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pokosho.PokoshoException;

import net.java.ao.EntityManager;

public class DBUtil {
	private static EntityManager manager;
	private static String dbUri;
	private static String dbUser;
	private static String dbPassword;
	private static final String KEY_DB_URI = "db.uri";
	private static final String KEY_DB_USER = "db.user";
	private static final String KEY_DB_PASSWORD = "db.password";
	private DBUtil() {
	}
	public static EntityManager getEntityManager(String propPath) throws PokoshoException {
		if (manager == null) {
			loadProp(propPath);
			//manager = new EntityManager("jdbc:hsqldb:hsql://localhost", "sa", "");
			manager = new EntityManager(dbUri, dbUser, dbPassword);

			// ActiveObjects のロガーを取得
			Logger logger = Logger.getLogger("net.java.ao");
			// ログレベルの設定
			logger.setLevel(Level.WARNING);
		}
		return manager;
	}

	public static EntityManager getEntityManager() throws PokoshoException {
		if (manager == null) {
			throw new PokoshoException("EntityManager isn't loaded.");
		}
		return manager;
	}

	private static void loadProp(String propPath) throws PokoshoException {
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(propPath));
			dbUri = prop.getProperty(KEY_DB_URI);
			dbPassword = prop.getProperty(KEY_DB_PASSWORD);
			dbUser = prop.getProperty(KEY_DB_USER);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new PokoshoException(e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new PokoshoException(e);
		}
	}
}
