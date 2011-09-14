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
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserStreamAdapter;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

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
	private static final String WORK_LAST_FOLLOW_FILE = "waketi_last_follow.txt";
	private static final int FOLLOW_INTERVAL_MSEC = 60 * 60 * 24 * 1000; // フォロー返しの間隔
	private static final int STATUS_MAX_COUNT = 200;

	private Twitter twitter;
	private String consumerKey;
	private String consumerSecret;
	private String accessToken;
	private String accessTokenSecret;
	private long selfUser;

	private static final String KEY_CONSUMER_KEY = "twitter.consumer.key";
	private static final String KEY_CONSUMER_SECRET = "twitter.consumer.secret";
	private static final String KEY_ACCESS_TOKEN = "twitter.access.token";
	private static final String KEY_ACCESS_TOKEN_SECRET = "twitter.access.secret";

	public TwitterBot(String dbPropPath, String botPropPath)
			throws PokoshoException {
		super(dbPropPath, botPropPath);
		loadProp(botPropPath);

		ConfigurationBuilder builder = new ConfigurationBuilder();
	    builder.setOAuthConsumerKey( consumerKey );
	    builder.setOAuthConsumerSecret( consumerSecret );
	    builder.setOAuthAccessToken( accessToken );
	    builder.setOAuthAccessTokenSecret( accessTokenSecret );
	    Configuration conf = builder.build();
		twitter = new TwitterFactory(conf).getInstance();
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

	private static final boolean streamMode = true;
	public void reply() throws PokoshoException {
		if (streamMode) {
			replyStream();
		} else {
			replySeq();
		}
	}

	/**
	 * 返信.
	 * @throws PokoshoException
	 */
	private void replySeq() throws PokoshoException {
		String s = null;
		long id = loadLastRead(WORK_LAST_READ_MENTION_FILE);
		Paging page = new Paging();
		page.setCount(STATUS_MAX_COUNT);
		ResponseList<Status> mentionList;
		long selfID;
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
				if (from.getId() <= id) {
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
				StatusUpdate us = new StatusUpdate("@" + from.getUser().getScreenName() + " " + s);
				us.setInReplyToStatusId(from.getId());
				twitter.updateStatus(us);
			} catch (TwitterException e) {
				new PokoshoException(e);
			}
		}
	}

	/**
	 * 返信(stream).
	 * @throws PokoshoException
	 */
	private void replyStream() throws PokoshoException {
		ConfigurationBuilder builder = new ConfigurationBuilder();
	    builder.setOAuthConsumerKey( consumerKey );
	    builder.setOAuthConsumerSecret( consumerSecret );
	    builder.setOAuthAccessToken( accessToken );
	    builder.setOAuthAccessTokenSecret( accessTokenSecret );
	    Configuration conf = builder.build();
		TwitterStreamFactory factory = new TwitterStreamFactory(conf);
	    TwitterStream twitterStream = factory.getInstance();
	    try {
			twitterStream.addListener(new MentionEventListener(selfUser));
		} catch (TwitterException e) {
			throw new PokoshoException(e);
		}
	    // start streaming
	    log.info("start twitterStream.user() ------------------------------");
	    twitterStream.user();
	}

	/**
	 * HomeTimeLineから学習する.
	 * TODO:パラメータクラスをつくって、リストを指定できるようにする
	 */
	@Override
	public void study(String str) throws PokoshoException {
		IDs frends;
		IDs follower;
		log.info("start study ------------------------------------");
		try {
			// WORKファイルのタイムスタンプを見て、指定時間経っていたら、フォロー返しを実行
			File f = new File(WORK_LAST_READ_FILE);
			if (!f.exists() || System.currentTimeMillis() < f.lastModified() + FOLLOW_INTERVAL_MSEC) {
				log.debug("selfUser:" + selfUser);
				frends = twitter.getFriendsIDs(selfUser, 0);
				follower = twitter.getFollowersIDs(selfUser, 0);
				List<Long> notFollowIdList = calcNotFollow(follower, frends);
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
					if (s.getId() <= id) {
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
					log.error("io error",e);
				} catch (SQLException e) {
					log.error("sql error", e);
				}
			}
		} catch (TwitterException e) {
			log.error("twitter error", e);
		}
		log.info("end study ------------------------------------");
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
			log.error("io error",e);
		} catch (NumberFormatException e) {
			log.error("number format error",e);
		} finally {
			try {
				if (br != null) br.close();
				if (filereader != null) filereader.close();
			} catch (IOException e) {
				log.error("io error",e);
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
			log.error("file not found error",e);
			throw new PokoshoException(e);
		} catch (IOException e) {
			log.error("io error",e);
			throw new PokoshoException(e);
		}
	}

	private void doFollow(List<Long> notFollowIdList) {
		for (Long userId : notFollowIdList) {
			User user = null;
			try {
				user = twitter.createFriendship(userId);
				if (user == null) {
					log.error("failed to follow：" + userId);
				}
				log.info("followed:" + user.getName());
			} catch (TwitterException e) {
				log.error("twitter error",e);
			}
		}
	}

	private List<Long> calcNotFollow(IDs follower, IDs frends) {
		List<Long> returnValue = new ArrayList<Long>();
		long lastFollow = loadLastRead(WORK_LAST_FOLLOW_FILE);
		log.debug("follower count:" + follower.getIDs().length + " frends count:" + frends.getIDs().length);
		for (long id : follower.getIDs()) {
			if (lastFollow == id) break; // 最後にフォローしたところまで読んだ
			if (!contains(frends, id)) {
				returnValue.add(Long.valueOf(id));
			}
		}
		if (0 < follower.getIDs().length) {
			saveLastRead(follower.getIDs()[0], WORK_LAST_FOLLOW_FILE);
		}
		return returnValue;
	}

	private boolean contains(IDs frends, long id) {
		for (long frendId : frends.getIDs()) {
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
			if (0 < args.length) {
				String mode = args[0];
				if (mode.equals("-c")) {
					b.cleaning();
				} else if (mode.equals("-s")) {
					b.reply();
				}
			} else {
				b.study(null);
				b.say();
				// b.reply(); // streamingで行う -s
			}
		} catch (Exception e) {
			log.error("system error", e);
		}
	}

	private class MentionEventListener extends UserStreamAdapter {
		private long selfUser;
		private String selfScreenName;
		public MentionEventListener(long selfUser) throws TwitterException {
			this.selfUser = selfUser;
			selfScreenName = twitter.showUser(selfUser).getScreenName();
		}

		@Override
		public void onStatus(Status from) {
			super.onStatus(from);
			String tweet = from.getText();
			if (!tweet.contains("@" + selfScreenName)) return;
			String s = null;
			log.info("onStatus user:" + from.getUser().getScreenName() + " tw:" + tweet);
			try {
				log.info("start getId");
				if (from.getUser().getId() == selfUser) {
					log.info("reply to self. nothing todo");
					return;
				}
				log.info("end getId");
				Status fromfrom = null;
				if (0 < from.getInReplyToStatusId()) {
					log.info("start showStatus");
					fromfrom = twitter.showStatus(from.getInReplyToStatusId());
					log.info("end showStatus");
				}
				// リプライ元、リプライ先を連結してもっともコストが高い単語を使う
				s = from.getText();
				if (fromfrom != null) {
					s = s +  "。" + fromfrom.getText();
				}
				// @xxx を削除
				s = TwitterUtils.removeMention(s);
				s = TwitterUtils.removeRTString(s);
				s = TwitterUtils.removeUrl(s);
				s = TwitterUtils.removeHashTags(s);
				log.info("start say against:" + s);
				s = say(s);
				log.info("end say");
				if (s == null || s.length() == 0) {
					log.info("no word");
					return;
				}
				log.info("updateStatus:" + s);
				StatusUpdate us = new StatusUpdate("@" + from.getUser().getScreenName() + " " + s);
				us.setInReplyToStatusId(from.getId());
				twitter.updateStatus(us);
			} catch (TwitterException e) {
				log.error("twitter error",e);
			} catch (PokoshoException e) {
				log.error("system error",e);
			} catch (Exception e) {
				log.error("system error(debug)",e);
			}
		}
	}
}
