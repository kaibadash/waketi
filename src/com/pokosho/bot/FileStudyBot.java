package com.pokosho.bot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;

import com.pokosho.PokoshoException;

/**
 * ファイルパスから文字列を読み込み学習する基本的なBot.
 * @author kaiba
 *
 */
public class FileStudyBot extends AbstractBot {

	public FileStudyBot(String dbPropPath, String botPropPath) throws PokoshoException {
		super(dbPropPath, botPropPath);
	}

	@Override
	public void study(String file) throws PokoshoException {
		Connection conn = null;
		try {
			FileReader freader = new FileReader(file);
			BufferedReader reader = new BufferedReader(freader);
			String line = null;
			do {
				conn = manager.getProvider().getConnection();
				line = reader.readLine();
				studyFromLine(line);
				conn.close();
			} while (line != null);
		} catch (Exception e) {
			throw new PokoshoException(e);
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException e) {
				throw new PokoshoException(e);
			}
		}
	}
}
