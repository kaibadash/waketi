package com.pokosho.bot.twitter;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.http.AccessToken;

import com.pokosho.PokoshoException;
import com.pokosho.bot.AbstractBot;

public class TwitterBot extends AbstractBot {
	private Twitter twitter;
	private static final String CONSUMER_KEY = "TODO";
	private static final String CONSUMER_SECRET = "TODO";
	private static final String ACCESS_TOKEN = "TODO";
	private static final String ACCESS_TOKEN_SECRET = "TODO";
	public TwitterBot() throws PokoshoException {
		super();
		AccessToken token = new AccessToken(ACCESS_TOKEN, ACCESS_TOKEN_SECRET);
		twitter = new TwitterFactory().getOAuthAuthorizedInstance(CONSUMER_KEY, CONSUMER_SECRET, token);
	}

	@Override
	public String say() throws PokoshoException {
		String s = super.say();
		try {
			twitter.updateStatus(s);
		} catch (TwitterException e) {
			new PokoshoException(e);
		}
		return s;
	}

	@Override
	public String say(String from) {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void study(String str) throws PokoshoException {
		// TODO 自動生成されたメソッド・スタブ

	}
}
