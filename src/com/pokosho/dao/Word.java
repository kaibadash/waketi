package com.pokosho.dao;

import net.java.ao.Entity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

public interface Word extends Entity {
	@PrimaryKey
	@NotNull
	@AutoIncrement
	public Integer getWord_ID();
	@NotNull
	public Integer getPos_ID();
	public void setPos_ID(Integer posID);
	@NotNull
	public String getWord();
	public void setWord(String word);

	public Integer getWord_Count();
	public void setWord_Count(Integer count);

	public void setTime(Integer t);
	public Integer getTime();
}
