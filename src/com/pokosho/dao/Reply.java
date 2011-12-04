package com.pokosho.dao;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

public interface Reply extends Entity {
	@PrimaryKey
	@NotNull
	public Long getUser_ID();
	public void setUser_ID(Long userID);

	public Long getTweet_ID();
	public void setTweet_ID(Long tweetID);

	public void setTime(Integer t);
	public Integer getTime();
}
