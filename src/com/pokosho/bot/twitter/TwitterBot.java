package com.pokosho.bot.twitter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.http.AccessToken;

import com.pokosho.PokoshoException;
import com.pokosho.bot.AbstractBot;
import com.pokosho.util.StringUtils;

public class TwitterBot extends AbstractBot {
	private static Logger log = LoggerFactory.getLogger(TwitterBot.class);
	private final static String DB_PROP = "./conf/db.properties";
	private final static String BOT_PROP = "./conf/bot.properties";
	private final static String LOG_PROP = "./conf/log.properties";
	private static final boolean DEBUG = false; // かならず全処理を行う
	private static final String WORK_LAST_READ_FILE = "waketi_last_read.txt";
	private static final String WORK_LAST_READ_MENTION_FILE = "waketi_last_read_mention.txt";
	private static final int FOLLOW_INTERVAL_MSEC = 60 * 60 * 24 * 1000; // フォロー返しの間隔
	private static final int STATUS_MAX_COUNT = 200;

	private Twitter twitter;
	private String consumerKey;
	private String consumerSecret;
	private String accessToken;
	private String accessTokenSecret;
	private int selfUser;

	private static final String KEY_CONSUMER_KEY = "twitter.consumer.key";
	private static final String KEY_CONSUMER_SECRET = "twitter.consumer.secret";
	private static final String KEY_ACCESS_TOKEN = "twitter.access.token";
	private static final String KEY_ACCESS_TOKEN_SECRET = "twitter.access.secret";

	public TwitterBot(String dbPropPath, String botPropPath)
			throws PokoshoException {
		super(dbPropPath, botPropPath);
		loadProp(botPropPath);
		AccessToken token = new AccessToken(accessToken, accessTokenSecret);
		twitter = new TwitterFactory().getOAuthAuthorizedInstance(consumerKey,
				consumerSecret, token);
		try {
			selfUser = twitter.getId();
		} catch (IllegalStateException e) {
			throw new PokoshoException(e);
		} catch (TwitterException e) {
			throw new PokoshoException(e);
		}
	}

	@Override
	public String say() throws PokoshoException {
		String s = super.say();
		try {
			if (s == null || s.length() == 0) {
				log.error("no word");
				return null;
			}
			log.info("updateStatus:" + s);
			twitter.updateStatus(s);
		} catch (TwitterException e) {
			new PokoshoException(e);
		}
		return s;
	}

	public void reply() throws PokoshoException {
		String s = null;
		long id = loadLastRead(WORK_LAST_READ_MENTION_FILE);
		Paging page = new Paging();
		page.setCount(STATUS_MAX_COUNT);
		ResponseList<Status> mentionList;
		int selfID;
		try {
			selfID = twitter.getId();
			mentionList = twitter.getMentions(page);
		} catch (TwitterException e1) {
			throw new PokoshoException(e1);
		}
		Status last = mentionList.get(0);
		saveLastRead(last.getId(),WORK_LAST_READ_MENTION_FILE);
		for (Status from : mentionList) {
			try {
				if (from.getId() == id) {
					log.debug("found last mention id:" + id);
					break;
				}
				if (from.getUser().getId() == selfID) continue;
				Status fromfrom = null;
				if (0 < from.getInReplyToStatusId()) {
					fromfrom = twitter.showStatus(from.getInReplyToStatusId());
				}
				// リプライ元、リプライ先を連結してもっともコストが高い単語を使う
				s = from.getText();
				if (fromfrom != null) {
					s = s +  "。" + fromfrom.getText();
				}
				// @xxx を削除
				s = TwitterUtils.removeMention(s);
				s = super.say(s);
				if (s == null || s.length() == 0) {
					log.error("no word");
					continue;
				}
				log.info("updateStatus:" + s);
				twitter.updateStatus("@" + from.getUser().getScreenName() + " " + s, from.getId());
			} catch (TwitterException e) {
				new PokoshoException(e);
			}
		}
	}

	/**
	 * HomeTimeLineから学習する.
	 * TODO:パラメータクラスをつくって、リストを指定できるようにする
	 */
	@Override
	public void study(String str) throws PokoshoException {
		IDs frends;
		IDs follower;
		try {
			// WORKファイルのタイムスタンプを見て、指定時間経っていたら、フォロー返しを実行
			File f = new File(WORK_LAST_READ_FILE);
			if (!f.exists() || System.currentTimeMillis() < f.lastModified() + FOLLOW_INTERVAL_MSEC) {
				frends = twitter.getFriendsIDs();
				follower = twitter.getFollowersIDs();
				List<Integer> notFollowIdList = calcNotFollow(follower, frends);
				doFollow(notFollowIdList);
			}

			// HomeTimeLine取得
			long id = loadLastRead(WORK_LAST_READ_FILE);
			Paging page = new Paging();
			page.setCount(STATUS_MAX_COUNT);
			ResponseList<Status> homeTimeLineList = twitter.getHomeTimeline(page);
			Status last = homeTimeLineList.get(0);
			saveLastRead(last.getId(),WORK_LAST_READ_FILE);
			log.info("size of homeTimelineList:" + homeTimeLineList.size());
			for (Status s : homeTimeLineList) {
				if (!DEBUG) {
					if (s.getId() == id) {
						log.info("found last tweet. id:" + id);
						break;
					}
				}
				try {
					if (s.getUser().getId() == selfUser) continue;
					String tweet = s.getText();
					if (TwitterUtils.isSpamTweet(tweet)) continue;
					tweet = TwitterUtils.removeHashTags(tweet);
					tweet = TwitterUtils.removeUrl(tweet);
					tweet = TwitterUtils.removeMention(tweet);
					tweet = TwitterUtils.removeRTString(tweet);
					String[] splited = tweet.split("。");
					// 「。」で切れたところで文章の終わりとする
					for (String msg : splited) {
						studyFromLine(msg);
					}
				} catch (IOException e) {
					log.error(e.toString());
				} catch (SQLException e) {
					log.error(e.toString());
				}
			}
		} catch (TwitterException e) {
			log.error(e.toString());
		}
	}

	private void saveLastRead(long id, String path) {
		PrintWriter pw = null;
		BufferedWriter bw = null;
		FileWriter filewriter = null;
		try {
			File file = new File(path);
			filewriter = new FileWriter(file);
			bw = new BufferedWriter(filewriter);
			pw = new PrintWriter(bw);
			pw.print(id);
		} catch (IOException e) {
			log.error(e.toString());
		} finally {
			try {
				if (pw != null) pw.close();
				if (bw != null) bw.close();
				if (filewriter != null)	filewriter.close();
			} catch (Exception e) {
				log.error(e.toString());
			}
		}
	}

	private long loadLastRead(String path) {
		long id = 0;
		FileReader filereader = null;
		BufferedReader br = null;
		try {
			File file = new File(path);
			if (!file.exists()) return 0;
			filereader = new FileReader(file);
			br = new BufferedReader(filereader);
			id = Long.valueOf(br.readLine());
		} catch (IOException e) {
			log.error(e.toString());
		} catch (NumberFormatException e) {
			log.error(e.toString());
		} finally {
			try {
				if (br != null) br.close();
				if (filereader != null) filereader.close();
			} catch (IOException e) {
				log.error(e.toString());
			}
		}
		return id;
	}

	private void loadProp(String propPath) throws PokoshoException {
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(propPath));
			consumerKey = prop.getProperty(KEY_CONSUMER_KEY);
			consumerSecret = prop.getProperty(KEY_CONSUMER_SECRET);
			accessToken = prop.getProperty(KEY_ACCESS_TOKEN);
			accessTokenSecret = prop.getProperty(KEY_ACCESS_TOKEN_SECRET);
		} catch (FileNotFoundException e) {
			log.error(e.toString());
			throw new PokoshoException(e);
		} catch (IOException e) {
			log.error(e.toString());
			throw new PokoshoException(e);
		}
	}

	private void doFollow(List<Integer> notFollowIdList) {
		for (Integer userId : notFollowIdList) {
			User user = null;
			try {
				user = twitter.createFriendship(userId);
				if (user == null) {
					log.error("failed to follow：" + userId);
				}
				log.info("followed:" + user.getName());
			} catch (TwitterException e) {
				log.error(e.toString());
			}
		}
	}

	private List<Integer> calcNotFollow(IDs follower, IDs frends) {
		List<Integer> returnValue = new ArrayList<Integer>();
		for (int id : follower.getIDs()) {
			if (!contains(frends, id)) {
				returnValue.add(Integer.valueOf(id));
			}
		}
		return returnValue;
	}

	private boolean contains(IDs frends, int id) {
		for (int frendId : frends.getIDs()) {
			if (frendId == id) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ホームタイムラインから学習し、1回ツイートします.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("file.encoding", StringUtils.ENCODE_STRING);
			System.setProperty("java.util.logging.config.file", LOG_PROP);
			System.setProperty("twitter4j.loggerFactory", "twitter4j.internal.logging.NullLoggerFactory");

			TwitterBot b = new TwitterBot(DB_PROP, BOT_PROP);
			b.study(null);
			b.say();
			b.reply();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
