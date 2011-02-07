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

	public FileStudyBot() throws PokoshoException {
		super();
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

/*

// debug sql
SELECT c.prefix01, w1.word_id, w1.word,
c.prefix02, w2.word_id, w2.word,
c.safix, w3.word_id, w3.word
 FROM   chain c
 INNER JOIN word w1
 ON c.prefix01 = w1.word_id
 INNER JOIN word w2
 ON c.prefix02 = w2.word_id
 INNER JOIN word w3
 ON c.safix = w3.word_id;

*/