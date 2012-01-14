package com.pokosho.bot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.java.ao.DBParam;
import net.java.ao.EntityManager;
import net.java.ao.Query;
import net.java.sen.StringTagger;
import net.java.sen.Token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokosho.PokoshoException;
import com.pokosho.bot.twitter.TwitterUtils;
import com.pokosho.dao.Chain;
import com.pokosho.dao.Word;
import com.pokosho.db.DBUtil;
import com.pokosho.db.Pos;
import com.pokosho.db.TableInfo;
import com.pokosho.util.StrRep;
import com.pokosho.util.StringUtils;

public abstract class AbstractBot {
	private static Logger log = LoggerFactory.getLogger(AbstractBot.class);
	protected static final int CHAIN_COUNT = 3;
	protected StringTagger tagger;
	protected static EntityManager manager;
	protected StrRep strRep;
	protected Properties prop;

	public AbstractBot(String dbPropPath, String botPropPath)
			throws PokoshoException {
		prop = new Properties();
		log.debug("dbPropPath:" + dbPropPath);
		log.debug("botPropPath:" + botPropPath);
		try {
			prop.load(new FileInputStream(botPropPath));
		} catch (FileNotFoundException e1) {
			log.error("FileNotFoundException", e1);
			throw new PokoshoException(e1);
		} catch (IOException e1) {
			log.error("IOException", e1);
			throw new PokoshoException(e1);
		}
		System.setProperty("sen.home", prop.getProperty("com.pokosho.sendir"));
		strRep = new StrRep(prop.getProperty("com.pokosho.repstr"));
		try {
			manager = DBUtil.getEntityManager(dbPropPath);
			tagger = StringTagger.getInstance();
		} catch (IllegalArgumentException e) {
			log.error("system error", e);
			throw new PokoshoException(e);
		} catch (IOException e) {
			log.error("system error", e);
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
		String result = null;
		try {
			Chain[] chain = manager.find(
					Chain.class,
					Query.select()
							.where(TableInfo.TABLE_CHAIN_START + " = ?", true)
							.order("rand() limit 1"));
			if (chain == null || chain.length == 0) {
				log.info("bot knows no words.");
				return null;
			}
			// 終了まで文章を組み立てる
			LinkedList<Integer> idList = new LinkedList<Integer>(
					createWordIDList(chain));
			result = createWordsFromIDList(idList);
			result = strRep.rep(result);
		} catch (SQLException e) {
			throw new PokoshoException(e);
		}
		return result;
	}

	/**
	 * 返事をする.
	 *
	 * @param from
	 *            返信元メッセージ.
	 * @return 返事.
	 */
	public synchronized String say(String from) throws PokoshoException {
		try {
			log.debug("reply base:" + from);
			from = StringUtils.simplizeForReply(from);
			log.debug("simplizeForReply:" + from);
			Token[] token = tagger.analyze(from);
			if (token == null) {
				return null;
			}
			Token minCostToken = null;
			Token minNounCostToken = null; // 最もコストの低い(珍しい)名詞
			Token maxNounCountToken = null; // 最もカウントが多い名詞
			Map<String, Integer> tokenCount = new HashMap<String, Integer>(); // 頻出単語
			int maxCount = 0;
			for (Token t : token) {
				log.debug("surface:" + t.getSurface() + " cost:" + t.getCost() + " pos:" + t.getPos());
				// 名詞でコストが高い言葉
				Pos tPos = StringUtils.toPos(t.getPos());
				if (tPos == Pos.Noun) {
					if (minNounCostToken == null || t.getCost() < minNounCostToken.getCost()) {
						minNounCostToken = t;
						log.debug("selected 名詞:" + minNounCostToken.getSurface());
					}
					// 出現回数のカウント
					if(!tokenCount.containsKey(t.getSurface())) {
						tokenCount.put(t.getSurface(), 1);
					} else {
						int c = tokenCount.get(t.getSurface()) + 1;
						if (maxCount < c) {
							maxCount = c;
							tokenCount.put(t.getSurface(), c);
							maxNounCountToken = t;
						}
					}
				}
				// costが高い言葉(名詞を選んでも意味が無いので名詞は除く)
				if (tPos != Pos.Noun && tPos != Pos.Preposition && tPos != Pos.Conjunction && tPos != Pos.Joshi) {
					if (minCostToken == null || minCostToken.getCost() > t.getCost()) {
						minCostToken = t;
					}
				}
			}
			// コストが低い単語
			log.info("selected max cost word:" +
					(minCostToken != null ? minCostToken.getSurface():"null") +
					" selected max cost noun word:" + (minNounCostToken != null ? minNounCostToken.getSurface():"noun not found.") +
					" max count:" + (maxNounCountToken != null ? maxNounCountToken.getSurface() : "null"));
			// 使用するtokenの決定
			Token targetToken = null;
			if (1 < maxCount) {
				targetToken = maxNounCountToken;
			} else if (minNounCostToken != null) {
				targetToken = minNounCostToken;
			} else if (minCostToken != null) {
				targetToken = minCostToken;
			} else {
				return null;
			}

			// 最大コストの単語で始まっているか調べて、始まっていたら使う
			Word[] word = null;
			word = manager.find(
					Word.class,
					Query.select().where(TableInfo.TABLE_WORD_WORD + " = ?",
							targetToken.getSurface()));
			if (word == null || word.length == 0) {
				log.info("max cost nown word was not found. use maxCostToken");
				if (minCostToken != null) {
					word = manager.find(
							Word.class,
							Query.select().where(TableInfo.TABLE_WORD_WORD + " = ?",
									targetToken.getSurface()));
					if (word == null || word.length == 0) {
						log.info("no word!");
						return null;
					}
				}
				return null;
			}
			// word found, start creating chain
			Chain[] chain = manager.find(
					Chain.class,
					Query.select().where(
							TableInfo.TABLE_CHAIN_START + " = ? and "
									+ TableInfo.TABLE_CHAIN_PREFIX01 + " = ?",
							true, word[0].getWord_ID()));
			boolean startWithMaxCountWord = false;
			if (chain == null || chain.length == 0) {
				chain = manager.find(
						Chain.class,
						Query.select().where(
								TableInfo.TABLE_CHAIN_PREFIX01 + " = ?",
								word[0].getWord_ID()));
				if (chain == null || chain.length == 0) {
					// まずあり得ないが保険
					log.debug("chain which is prefix01 or suffix wasn't found:"
							+ word[0].getWord_ID());
					return null;
				}
			} else {
				startWithMaxCountWord = true;
			}

			// 終了まで文章を組み立てる
			LinkedList<Integer> idList = new LinkedList<Integer>(
					createWordIDList(chain));
			if (!startWithMaxCountWord) {
				// 先頭まで組み立てる
				idList = createWordIDListEndToStart(idList, chain);
			}
			String result = createWordsFromIDList(idList);
			result = strRep.rep(result);
			return result;
		} catch (SQLException e) {
			throw new PokoshoException(e);
		} catch (IOException e) {
			throw new PokoshoException(e);
		}
	}

	protected Token[] studyFromLine(String str) throws IOException,
			SQLException {
		log.info("studyFromLine:" + str);
		// スパム判定
		if (str == null || str.length() < 0) {
			return null;
		}
		if (!TwitterUtils.containsJPN(str)) {
			log.debug("it's not Japanese");
			return null;
		}
		if (TwitterUtils.isSpamTweet(str)) {
			log.debug("spam tweet:" + str);
			return null;
		}
		str = StringUtils.simplize(str);
		Token[] token = tagger.analyze(str);
		if (token == null) {
			return null;
		}
		Integer[] chainTmp = new Integer[CHAIN_COUNT];
		for (int i = 0; i < token.length; i++) {
			log.debug(token[i].getBasicString() + "(" + token[i].getTermInfo()
					+ ")");
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
		// EOF
		createChain(chainTmp[1], chainTmp[2], null, false);
		return token;
	}

	protected void createChain(final Integer prefix01, final Integer prefix02,
			final Integer safix, final Boolean start) throws SQLException {
		log.debug(String.format("createChain:%d,%d,%d", prefix01, prefix02,
				safix));
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
			manager.create(Chain.class, new DBParam(
					TableInfo.TABLE_CHAIN_PREFIX01, prefix01), new DBParam(
					TableInfo.TABLE_CHAIN_PREFIX02, prefix02), new DBParam(
					TableInfo.TABLE_CHAIN_START, start));
		} else {
			manager.create(Chain.class, new DBParam(
					TableInfo.TABLE_CHAIN_PREFIX01, prefix01), new DBParam(
					TableInfo.TABLE_CHAIN_PREFIX02, prefix02), new DBParam(
					TableInfo.TABLE_CHAIN_SUFFIX, safix), new DBParam(
					TableInfo.TABLE_CHAIN_START, start));
		}
	}

	/**
	 * ゴミ掃除をする.
	 */
	protected void cleaning() throws SQLException, IOException {
		String cleaningFile = prop.getProperty("com.pokosho.cleaning");
		File file = null;
		FileReader filereader = null;
		BufferedReader br = null;
		try {
			file = new File(cleaningFile);
			filereader = new FileReader(file);
			br = new BufferedReader(filereader);
			String line = null;
			while ((line = br.readLine()) != null) {
				Word[] words = manager.find(
						Word.class,
						Query.select().where(
								TableInfo.TABLE_WORD_WORD + " like '" + line
										+ "%'"));
				log.info("search " + TableInfo.TABLE_WORD_WORD + " like '"
						+ line + "%'");
				// カンマ区切りにする
				StringBuffer sb = new StringBuffer();
				for (Word w : words) {
					sb.append(w.getWord_ID() + ",");
					log.info("delete word:" + w.getWord() + " ID:"
							+ w.getWord_ID());
				}
				if (sb.length() == 0)
					continue;
				sb.deleteCharAt(sb.length() - 1); // 末尾の 「,」 を取り除く
				manager.delete(manager.find(
						Chain.class,
						Query.select().where(
								TableInfo.TABLE_CHAIN_PREFIX01 + " in (?) OR "
										+ TableInfo.TABLE_CHAIN_PREFIX02
										+ " in (?) OR "
										+ TableInfo.TABLE_CHAIN_SUFFIX
										+ " in (?)", sb.toString(),
								sb.toString(), sb.toString())));
			}
		} finally {
			if (br != null)
				br.close();
			if (filereader != null)
				filereader.close();
		}
	}

	/**
	 * 単語のIDリストを作成する
	 *
	 * @throws SQLException
	 */
	private List<Integer> createWordIDList(Chain[] startChain)
			throws SQLException {
		int chainCountDown = Integer.parseInt(prop
				.getProperty("com.pokosho.chain_count_down"));
		int loopCount = 0;
		List<Integer> idList = new ArrayList<Integer>();
		idList.add(startChain[0].getPrefix01());
		idList.add(startChain[0].getPrefix02());
		Chain[] chain = startChain;
		while (true) {
			log.debug("pick next chain which has prefix01 id "
					+ chain[0].getSuffix());
			if (chain[0].getSuffix() == null) {
				break;
			}
			// 指定回数連鎖したら終端を探しに行く
			String whereEnd = "";
			if (loopCount > chainCountDown) {
				whereEnd = TableInfo.TABLE_CHAIN_SUFFIX + "=null and ";
			}
			Chain[] nextChain = manager.find(
					Chain.class,
					Query.select().where(
							whereEnd + TableInfo.TABLE_CHAIN_PREFIX01
									+ "=? order by rand() limit 1",
							chain[0].getSuffix()));
			// 終端が見つからなかった場合は、終端を探すのを諦める
			if (whereEnd.length() == 0 && nextChain == null
					|| nextChain.length == 0) {
				nextChain = manager.find(
						Chain.class,
						Query.select().where(
								TableInfo.TABLE_CHAIN_PREFIX01
										+ "=? order by rand() limit 1",
								chain[0].getSuffix()));
			}
			if (nextChain == null || nextChain.length == 0) {
				log.info("no next chain. please study more...");
				break;
			}
			idList.add(nextChain[0].getPrefix01());
			idList.add(nextChain[0].getPrefix02());
			log.debug("picked chain id " + nextChain[0].getChain_ID());
			chain = nextChain;
			loopCount++;
		}
		return idList;
	}

	/**
	 * 単語のIDリストを作成する
	 *
	 * @throws SQLException
	 */
	private LinkedList<Integer> createWordIDListEndToStart(
			LinkedList<Integer> idList, Chain[] startChain) throws SQLException {
		Chain[] chain = startChain;
		while (true) {
			if (chain[0].getStart()) {
				break;
			}
			// 始まりを探す
			Chain[] nextChain = manager.find(
					Chain.class,
					Query.select().where(
							TableInfo.TABLE_CHAIN_SUFFIX + "=? and "
									+ TableInfo.TABLE_CHAIN_START
									+ "=? limit 1", chain[0].getPrefix01(),
							true));
			if (nextChain == null || nextChain.length == 0) {
				// 無かったらランダムピック
				nextChain = manager.find(
						Chain.class,
						Query.select().where(
								TableInfo.TABLE_CHAIN_SUFFIX
										+ "=? order by rand() limit 1",
								chain[0].getPrefix01()));
			}
			if (nextChain == null || nextChain.length == 0) {
				log.info("no next chain. please study more...");
				break;
			}
			idList.add(0, nextChain[0].getPrefix01());
			idList.add(1, nextChain[0].getPrefix02());
			log.debug("picked chain id " + nextChain[0].getChain_ID());
			chain = nextChain;
		}
		return idList;
	}

	/**
	 * wordIDのリストから文章を作成する.
	 *
	 * @param idList
	 * @return
	 * @throws SQLException
	 */
	private String createWordsFromIDList(List<Integer> idList)
			throws SQLException {
		StringBuilder result = new StringBuilder();
		// wordの取得
		for (int i = 0; i < idList.size(); i++) {
			Word[] words = manager.find(
					Word.class,
					Query.select().where(TableInfo.TABLE_WORD_WORD_ID + "=?",
							idList.get(i)));
			char lastChar = words[0].getWord().charAt(
					words[0].getWord().length() - 1);
			// 半角英語の間にスペースを入れる
			if (result.length() != 0
					&& TwitterUtils.isAlfabet(lastChar)
					&& TwitterUtils
							.isAlfabet(result.charAt(result.length() - 1))) {
				result.append(" ");
			}
			result.append(words[0].getWord());
		}
		return result.toString();
	}
}
