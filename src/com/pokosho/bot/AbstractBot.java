package com.pokosho.bot;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.java.ao.DBParam;
import net.java.ao.EntityManager;
import net.java.ao.Query;
import net.java.sen.StringTagger;
import net.java.sen.Token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokosho.PokoshoException;
import com.pokosho.dao.Chain;
import com.pokosho.dao.Word;
import com.pokosho.db.DBUtil;
import com.pokosho.db.Pos;
import com.pokosho.db.TableInfo;
import com.pokosho.util.StringUtils;

public abstract class AbstractBot {
	private static Logger log = LoggerFactory.getLogger(AbstractBot.class);
	protected static final int CHAIN_COUNT = 3;
	protected StringTagger tagger;
	protected static EntityManager manager;

	public AbstractBot(String dbPropPath, String botPropPath) throws PokoshoException {
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(botPropPath));
		} catch (FileNotFoundException e1) {
			log.equals(e1);
			throw new PokoshoException(e1);
		} catch (IOException e1) {
			log.equals(e1);
			throw new PokoshoException(e1);
		}
		System.setProperty("sen.home", prop.getProperty("com.pokosho.sendir"));
		try {
			manager = DBUtil.getEntityManager(dbPropPath);
			tagger = StringTagger.getInstance();
		} catch (IllegalArgumentException e) {
			throw new PokoshoException(e);
		} catch (IOException e) {
			throw new PokoshoException(e);
		}
	}

	/**
	 * 学習する.
	 *
	 * @param file
	 * @throws PokoshoException
	 */
	public abstract void study(String str) throws PokoshoException;

	/**
	 * 発言を返す.
	 *
	 * @return 発言.
	 * @throws PokoshoException
	 */
	public String say() throws PokoshoException {
		StringBuilder result = new StringBuilder();
		Connection conn = null;
		try {
			conn = manager.getProvider().getConnection();
			Chain[] chain = manager.find(Chain.class,
					Query.select().where(TableInfo .TABLE_CHAIN_START + " = ?", true)
					.order("rand() limit 1"));
			if (chain == null || chain.length == 0) {
				log.debug("bot knows no words.");
				return null;
			}
			List<Integer> idList = new ArrayList<Integer>();
			idList.add(chain[0].getPrefix01());
			idList.add(chain[0].getPrefix02());
			while (true) {
				log.debug("pick next chain which has prefix01 id "
						+ chain[0].getSuffix());
				if (chain[0].getSuffix() == null) {
					break;
				}
				Chain[] nextChain = manager.find(
						Chain.class,
						Query.select().where(
								TableInfo.TABLE_CHAIN_PREFIX01
										+ "=? order by rand() limit 1",
								chain[0].getSuffix()));
				if (nextChain == null || nextChain.length == 0) {
					log.info("no next chain. please study more...");
					break;
				}
				Word[] posCheck = manager.find(
						Word.class,
						Query.select().where(
								TableInfo.TABLE_WORD_WORD_ID + "=? and "
										+ TableInfo.TABLE_WORD_WORD_POS_ID
										+ "=?", chain[0].getPrefix01(),
								Pos.Preposition));
				if (posCheck != null && 0 < posCheck.length) {
					chain = manager.find(Chain.class,
							Query.select().order("rand() limit 1"));
					continue; // TODO:もっと賢く…
				}
				idList.add(nextChain[0].getPrefix01());
				idList.add(nextChain[0].getPrefix02());
				log.debug("picked chain id "
						+ nextChain[0].getChain_ID());
				chain = nextChain;
			}

			// wordの取得
			for (int i = 0; i < idList.size(); i++) {
				Word[] words = manager.find(
						Word.class,
						Query.select().where(
								TableInfo.TABLE_WORD_WORD_ID + "=?",
								idList.get(i)));
				result.append(words[0].getWord());
			}

		} catch (SQLException e) {
			throw new PokoshoException(e);
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {
				throw new PokoshoException(e);
			}
		}
		return result.toString();
	}

	/**
	 * 返事をする.
	 * @param from 返信元メッセージ.
	 * @return 返事.
	 */
	public String say(String from) {
		// TODO:実装する
		return null;
	}

	protected void studyFromLine(String str) throws IOException, SQLException {
		log.info("studyFromLine:" + str);
		if (str == null || str.length() < 0) {
			return;
		}
		str = StringUtils.simplize(str);
		Token[] token = tagger.analyze(str);
		if (token == null) {
			return;
		}
		Integer[] chainTmp = new Integer[CHAIN_COUNT];
		for (int i = 0; i < token.length; i++) {
			log.debug(token[i].getBasicString() + "("
					+ token[i].getTermInfo() + ")");
			Word[] existWord = manager.find(
					Word.class,
					Query.select().where(TableInfo.TABLE_WORD_WORD + " = ?",
							token[i].getSurface()));
			if (existWord == null || existWord.length == 0) {
				// 新規作成
				Word newWord = manager.create(Word.class);
				newWord.setWord(token[i].getSurface());
				newWord.setWord_Count(1);
				newWord.setPos_ID((int) StringUtils.toPos(token[i].getPos())
						.getIntValue());
				newWord.setTime((int) (System.currentTimeMillis() / 1000));
				newWord.save();
				// IDを取得
				existWord = manager.find(
						Word.class,
						Query.select().where(
								TableInfo.TABLE_WORD_WORD + " = ?",
								token[i].getSurface())); // createで作っている時点でIDは分かるので無駄…
															// TODO
			} else {
				existWord[0].setWord_Count(existWord[0].getWord_Count() + 1);
				existWord[0].setTime((int) (System.currentTimeMillis() / 1000));
				existWord[0].save();
			}

			// chainができているかどうか
			if (2 < i) {
				// swap
				chainTmp[0] = chainTmp[1];
				chainTmp[1] = chainTmp[2];
				chainTmp[2] = existWord[0].getWord_ID();
				createChain(chainTmp[0], chainTmp[1], chainTmp[2], false);
			} else {
				// Chainを準備
				if (chainTmp[0] != null) {
					if (chainTmp[1] != null) {
						chainTmp[2] = existWord[0].getWord_ID(); // chain 完成
						createChain(chainTmp[0], chainTmp[1], chainTmp[2], true);
					} else {
						chainTmp[1] = existWord[0].getWord_ID();
					}
				} else {
					chainTmp[0] = existWord[0].getWord_ID();
				}
			}
		}
		// EOS
		createChain(chainTmp[1], chainTmp[2], null, false);
	}

	protected void createChain(final Integer prefix01, final Integer prefix02,
			final Integer safix, final Boolean start) throws SQLException {
		log.debug(String.format("createChain:%d,%d,%d", prefix01,
				prefix02, safix));
		if (prefix01 == null || prefix02 == null) {
			log.debug("prefix is null.");
			return;
		}
		Chain[] existChain;
		existChain = manager.find(
				Chain.class,
				Query.select().where(
						TableInfo.TABLE_CHAIN_PREFIX01 + "=? and "
								+ TableInfo.TABLE_CHAIN_PREFIX02 + "=? and "
								+ TableInfo.TABLE_CHAIN_SUFFIX + "=?",
						prefix01, prefix02, safix));
		if (existChain != null && 0 < existChain.length) {
			log.debug("chain exists.");
			return;
		}
		if (safix == null) {
			// 文章の終了
			manager.create(Chain.class,
					new DBParam(TableInfo.TABLE_CHAIN_PREFIX01, prefix01),
					new DBParam(TableInfo.TABLE_CHAIN_PREFIX02, prefix02),
					new DBParam(TableInfo.TABLE_CHAIN_START, start));
		} else {
			manager.create(Chain.class,
					new DBParam(TableInfo.TABLE_CHAIN_PREFIX01, prefix01),
					new DBParam(TableInfo.TABLE_CHAIN_PREFIX02, prefix02),
					new DBParam(TableInfo.TABLE_CHAIN_SUFFIX, safix),
					new DBParam(TableInfo.TABLE_CHAIN_START, start));
		}
	}

}
