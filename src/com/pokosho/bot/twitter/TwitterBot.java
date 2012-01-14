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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arnx.jsonic.JSON;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.sen.Token;

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
import com.pokosho.dao.Reply;
import com.pokosho.db.Pos;
import com.pokosho.db.TableInfo;
import com.pokosho.util.StringUtils;

public class TwitterBot extends AbstractBot {
	private static Logger log = LoggerFactory.getLogger(TwitterBot.class);
	private static final String DB_PROP = "./conf/db.properties";
	private static final String BOT_PROP = "./conf/bot.properties";
	private static final String LOG_PROP = "./conf/log.properties";
	private static final boolean DEBUG = false; // かならず全処理を行う
	private static final String WORK_LAST_READ_FILE = "waketi_last_read.txt";
	private static final String WORK_LAST_READ_MENTION_FILE = "waketi_last_read_mention.txt";
	private static final String WORK_LAST_FOLLOW_FILE = "waketi_last_follow.txt";
	private static final int FOLLOW_INTERVAL_MSEC = 60 * 60 * 3 * 1000; // フォロー返しの間隔
	private static final int STATUS_MAX_COUNT = 200;
	// TF-IDFのN. 以前のコストと比較するわけではないので定数で良い
	private static final int NUMBER_OF_DOCUMENT = 100000;
	private static Set<String> NOT_TREND;
	private static Set<String> spamWords;
	private int maxReplyCountPerHour = 10;
	private int maxReplyIntervalSec = 60 * 60;
	private static final int MIN_TWEET_FOR_FOLLOW = 100;
	private static final String SPAM_USER_LOG_LABEL = "spam user:";
	static {
		NOT_TREND = new HashSet<String>();
		NOT_TREND.add("の");
		NOT_TREND.add("を");
		NOT_TREND.add("こと");
	}

	private Twitter twitter;
	private String consumerKey;
	private String consumerSecret;
	private String accessToken;
	private String accessTokenSecret;
	private String trendPath;
	private String notTreacherPath;
	private String spamWordsPath;
	private User selfUser;

	private static final String KEY_CONSUMER_KEY = "twitter.consumer.key";
	private static final String KEY_CONSUMER_SECRET = "twitter.consumer.secret";
	private static final String KEY_ACCESS_TOKEN = "twitter.access.token";
	private static final String KEY_ACCESS_TOKEN_SECRET = "twitter.access.secret";
	private static final String KEY_TREND_PATH = "com.pokosho.trends";
	private static final String KEY_NOT_TEACHER_PATH = "com.pokosho.not_teacher";
	private static final String KEY_MAX_REPLY_COUNT = "com.pokosho.max_reply_count";
	private static final String KEY_REPLY_INTERVAL_MIN = "com.pokosho.max_reply_interval_sec";
	private static final String KEY_SPAM_WORDS = "com.pokosho.spamwords";

	public TwitterBot(String dbPropPath, String botPropPath)
			throws PokoshoException {
		super(dbPropPath, botPropPath);
		loadProp(botPropPath);

		ConfigurationBuilder builder = new ConfigurationBuilder();
		builder.setOAuthConsumerKey(consumerKey);
		builder.setOAuthConsumerSecret(consumerSecret);
		builder.setOAuthAccessToken(accessToken);
		builder.setOAuthAccessTokenSecret(accessTokenSecret);
		Configuration conf = builder.build();
		twitter = new TwitterFactory(conf).getInstance();
		try {
			long selfUserID = twitter.getId();
			selfUser = twitter.showUser(selfUserID);
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
	 *
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
		saveLastRead(last.getId(), WORK_LAST_READ_MENTION_FILE);
		for (Status from : mentionList) {
			try {
				if (from.getId() <= id) {
					log.debug("found last mention id:" + id);
					break;
				}
				if (from.getUser().getId() == selfID)
					continue;
				Status fromfrom = null;
				if (0 < from.getInReplyToStatusId()) {
					fromfrom = twitter.showStatus(from.getInReplyToStatusId());
				}
				// リプライ元、リプライ先を連結してもっともコストが高い単語を使う
				s = from.getText();
				if (fromfrom != null) {
					s = s + "。" + fromfrom.getText();
				}
				// @xxx を削除
				s = TwitterUtils.removeMention(s);
				s = super.say(s, NUMBER_OF_DOCUMENT);
				if (s == null || s.length() == 0) {
					log.error("no word");
					continue;
				}
				log.info("updateStatus:" + s);
				StatusUpdate us = new StatusUpdate("@"
						+ from.getUser().getScreenName() + " " + s);
				us.setInReplyToStatusId(from.getId());
				twitter.updateStatus(us);
			} catch (TwitterException e) {
				new PokoshoException(e);
			}
		}
	}

	/**
	 * 返信(stream).
	 *
	 * @throws PokoshoException
	 */
	private void replyStream() throws PokoshoException {
		ConfigurationBuilder builder = new ConfigurationBuilder();
		builder.setOAuthConsumerKey(consumerKey);
		builder.setOAuthConsumerSecret(consumerSecret);
		builder.setOAuthAccessToken(accessToken);
		builder.setOAuthAccessTokenSecret(accessTokenSecret);
		Configuration conf = builder.build();
		TwitterStreamFactory factory = new TwitterStreamFactory(conf);
		TwitterStream twitterStream = factory.getInstance();
		try {
			twitterStream.addListener(new MentionEventListener(selfUser.getId()));
		} catch (TwitterException e) {
			throw new PokoshoException(e);
		}
		// start streaming
		log.info("start twitterStream.user() ------------------------------");
		twitterStream.user();
	}

	/**
	 * HomeTimeLineから学習する.
	 */
	@Override
	public void study(String str) throws PokoshoException {
		IDs friends;
		IDs follower;
		Map<String, Integer> trendCountMap = new HashMap<String, Integer>();
		log.info("start study ------------------------------------");
		try {
			// WORKファイルのタイムスタンプを見て、指定時間経っていたら、フォロー返しを実行
			File f = new File(WORK_LAST_FOLLOW_FILE);
			long cTime = System.currentTimeMillis();
			spamWords = TwitterUtils.getStringSet(spamWordsPath);
			log.info("selfUser:" + selfUser + " currentTimeMillis:" + cTime
					+ " lastModified+FOLLOW_INTERVAL_MSEC:"
					+ (f.lastModified() + FOLLOW_INTERVAL_MSEC));
			if (!f.exists() || f.lastModified() + FOLLOW_INTERVAL_MSEC < cTime) {
				friends = twitter.getFriendsIDs(selfUser.getId(), -1);
				follower = twitter.getFollowersIDs(selfUser.getId(), -1);
				List<Long> notFollowIdList = calcNotFollow(follower, friends);
				doFollow(notFollowIdList);
			}

			// HomeTimeLine取得
			long id = loadLastRead(WORK_LAST_READ_FILE);
			Paging page = new Paging();
			page.setCount(STATUS_MAX_COUNT);
			ResponseList<Status> homeTimeLineList = twitter
					.getHomeTimeline(page);
			Status last = homeTimeLineList.get(0);
			saveLastRead(last.getId(), WORK_LAST_READ_FILE);
			log.info("size of homeTimelineList:" + homeTimeLineList.size());
			Pattern endsWithNumPattern = Pattern.compile(".*[0-9]+$",
					Pattern.CASE_INSENSITIVE);
			Set<Long> notTeachers = TwitterUtils
					.getNotTeachers(notTreacherPath);
			for (Status s : homeTimeLineList) {
				if (!DEBUG) {
					if (s.getId() <= id) {
						log.info("found last tweet. id:" + id);
						break;
					}
				}
				if (notTeachers.contains(s.getUser().getId())) {
					log.debug("not teacher:" + s.getUser().getId());
					continue;
				}
				try {
					if (s.getUser().getId() == selfUser.getId())
						continue;
					String tweet = s.getText();
					if (TwitterUtils.isSpamTweet(tweet))
						continue;
					tweet = TwitterUtils.removeHashTags(tweet);
					tweet = TwitterUtils.removeUrl(tweet);
					tweet = TwitterUtils.removeMention(tweet);
					tweet = TwitterUtils.removeRTString(tweet);
					String[] splited = tweet.split("。");
					// 「。」で切れたところで文章の終わりとする
					for (String msg : splited) {
						// トレンド用の集計
						Token[] token = studyFromLine(msg);
						if (token != null && 0 < token.length) {
							for (Token t : token) {
								if (StringUtils.toPos(t.getPos()) == Pos.Noun
										&& TwitterUtils.containsJPN(t
												.getSurface())
										&& !NOT_TREND.contains(t.getSurface())
										&& 1 < t.getSurface().length()) {
									int count = 0;
									if (trendCountMap.containsKey(t
											.getSurface())) {
										count = trendCountMap.get(t
												.getSurface());
									}
									count++;
									trendCountMap.put(t.getSurface(), count);
								}
							}
						}
						// 数字で終わるtweetは誰が教えているのか？
						Matcher matcher = endsWithNumPattern.matcher(msg);
						if (matcher.matches()) {
							log.info("found endswith number tweet:"
									+ s.getText() + " tweetID:" + s.getId());
						}
					}
				} catch (IOException e) {
					log.error("io error", e);
				} catch (SQLException e) {
					log.error("sql error", e);
				}
			}
			createTrend(trendCountMap);
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
				if (pw != null)
					pw.close();
				if (bw != null)
					bw.close();
				if (filewriter != null)
					filewriter.close();
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
			if (!file.exists())
				return 0;
			filereader = new FileReader(file);
			br = new BufferedReader(filereader);
			id = Long.valueOf(br.readLine());
		} catch (IOException e) {
			log.error("io error", e);
		} catch (NumberFormatException e) {
			log.error("number format error", e);
		} finally {
			try {
				if (br != null)
					br.close();
				if (filereader != null)
					filereader.close();
			} catch (IOException e) {
				log.error("io error", e);
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
			trendPath = prop.getProperty(KEY_TREND_PATH);
			notTreacherPath = prop.getProperty(KEY_NOT_TEACHER_PATH);
			spamWordsPath = prop.getProperty(KEY_SPAM_WORDS);

			maxReplyCountPerHour = Integer.parseInt(prop
					.getProperty(KEY_MAX_REPLY_COUNT));
			maxReplyIntervalSec = Integer.parseInt(prop
					.getProperty(KEY_REPLY_INTERVAL_MIN));
		} catch (FileNotFoundException e) {
			log.error("file not found error", e);
			throw new PokoshoException(e);
		} catch (IOException e) {
			log.error("io error", e);
			throw new PokoshoException(e);
		}
	}

	private void doFollow(List<Long> notFollowIdList) {
		for (Long userId : notFollowIdList) {
			User user = null;
			try {
				user = twitter.showUser(userId);
				if (isSpamUser(user)) {
					continue;
				}
				user = twitter.createFriendship(userId);
				if (user == null) {
					log.error("failed to follow：" + userId);
				}
				log.info("followed:" + user.getName());
			} catch (TwitterException e) {
				log.error("twitter error", e);
			}
		}
		if (0 < notFollowIdList.size()) {
			saveLastRead(notFollowIdList.get(notFollowIdList.size() - 1),
					WORK_LAST_FOLLOW_FILE);
		}
	}

	/**
	 * スパム判定.
	 * @param user
	 * @return
	 */
	private boolean isSpamUser(User user) {
		String prof = user.getDescription();
		if (prof.length() == 0) {
			log.info(SPAM_USER_LOG_LABEL + user.getScreenName() + " " + user.getId() + " has not profile.");
			return true;
		}
		if (!TwitterUtils.containsJPN(prof)) {
			log.info(SPAM_USER_LOG_LABEL + user.getScreenName() + " " + user.getId() + " has not profile in Japanese.");
			return true;
		}
		if (user.getStatusesCount() < MIN_TWEET_FOR_FOLLOW) {
			log.info(SPAM_USER_LOG_LABEL + user.getScreenName() + " " + user.getId() + " tweets few");
			return true;
		}
		for (String w : spamWords) {
			if (0 < prof.indexOf(w)) {
				log.info(SPAM_USER_LOG_LABEL + user.getScreenName() + " " + user.getId() + " has spam words:" + w);
				return true;
			}
		}
		return false;
	}

	private List<Long> calcNotFollow(IDs follower, IDs friends) {
		List<Long> returnValue = new ArrayList<Long>();
		long lastFollow = loadLastRead(WORK_LAST_FOLLOW_FILE);
		log.info("follower count:" + follower.getIDs().length
				+ " friends count:" + friends.getIDs().length);
		for (long id : follower.getIDs()) {
			if (lastFollow == id)
				break; // 最後にフォローしたところまで読んだ
			if (!contains(friends, id)) {
				log.info("follow user id:" + id);
				returnValue.add(Long.valueOf(id));
			}
		}
		return returnValue;
	}

	private boolean contains(IDs friends, long id) {
		for (long frendId : friends.getIDs()) {
			if (frendId == id) {
				return true;
			}
		}
		return false;
	}

	private static final int TREND_COUNT_MAX = 10;

	/**
	 * トレンドを作成.
	 *
	 * @param trendCountMap
	 */
	private void createTrend(Map<String, Integer> trendCountMap) {
		log.debug("createTrend trendCountMap.size:" + trendCountMap.size());
		Trend trends = new Trend();
		trendCountMap.entrySet();
		List<Entry<String, Integer>> entries = new ArrayList<Entry<String, Integer>>(
				trendCountMap.entrySet());
		Collections.sort(entries, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				return o2.getValue() - o1.getValue();
			}
		});
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		String dateStr = sdf.format(new Date());
		for (int i = 0; i < TREND_COUNT_MAX && i < entries.size(); i++) {
			Entry<String, Integer> entry = entries.get(i);
			trends.addTrend(dateStr, entry.getKey(), null, null, null);
			log.debug("trend rank " + i + ":" + entry.getKey() + "("
					+ entry.getValue());
		}
		String json = JSON.encode(trends);
		log.debug("trend json:" + json);
		// output to file
		FileWriter fw = null;
		try {
			fw = new FileWriter(new File(trendPath));
			fw.write(json);
		} catch (IOException e) {
			log.error("outputing trends failed", e);
		} finally {
			try {
				fw.close();
			} catch (Exception e) {
				log.error("closing trends file failed", e);
			}
		}
	}

	/**
	 * ホームタイムラインから学習し、1回ツイートします.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.setProperty("file.encoding", StringUtils.ENCODE_STRING);
			System.setProperty("java.util.logging.config.file", LOG_PROP);
			System.setProperty("twitter4j.loggerFactory",
					"twitter4j.internal.logging.NullLoggerFactory");

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
			String s = null;
			log.info("onStatus user:" + from.getUser().getScreenName() + " tw:"
					+ tweet);
			if (!tweet.contains("@" + selfScreenName)) {
				return;
			}
			// reply超過チェック
			try {
				// ユーザの一時間以内のreplyを取得
				Reply[] reply = manager.find(
						Reply.class,
						Query.select().where(
								TableInfo.TABLE_REPLY_USER_ID + " = ? and "
										+ TableInfo.TABLE_REPLY_TIME + " > ?",
								from.getUser().getId(), System.currentTimeMillis() / 1000 - maxReplyIntervalSec
								));
				if (reply != null) {
					log.debug("reply count:" + reply.length);
					if (reply != null && maxReplyCountPerHour < reply.length) {
						log.debug("user:" + from.getUser().getScreenName()
								+ " sent reply over " + maxReplyCountPerHour);
						return;
					}
				}
			} catch (SQLException e) {
				log.error("sql error", e);
				return;
			}
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
					s = s + "。" + fromfrom.getText();
				}
				// @xxx を削除
				s = TwitterUtils.removeMention(s);
				s = TwitterUtils.removeRTString(s);
				s = TwitterUtils.removeUrl(s);
				s = TwitterUtils.removeHashTags(s);
				log.info("start say against:" + s);
				s = say(s, NUMBER_OF_DOCUMENT);
				log.info("end say");
				if (s == null || s.length() == 0) {
					log.info("no word");
					return;
				}
				log.info("updateStatus:" + s);
				StatusUpdate us = new StatusUpdate("@"
						+ from.getUser().getScreenName() + " " + s);
				us.setInReplyToStatusId(from.getId());
				twitter.updateStatus(us);
			} catch (TwitterException e) {
				log.error("twitter error", e);
			} catch (PokoshoException e) {
				log.error("system error", e);
			} catch (Exception e) {
				log.error("system error(debug)", e);
			}
			// insert reply
			try {
				manager.create(
						Reply.class,
						new DBParam(TableInfo.TABLE_REPLY_TWEET_ID, from
								.getId()),
						new DBParam(TableInfo.TABLE_REPLY_USER_ID, from
								.getUser().getId()),
						new DBParam(TableInfo.TABLE_REPLY_TIME, (int) (System
								.currentTimeMillis() / 1000)));
			} catch (SQLException e) {
				log.error("insert reply error", e);
			}
		}
	}
}
