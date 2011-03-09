package com.pokosho.dao;

import net.java.ao.Entity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

/**
 * 三階のマルコフ.
 * @author kaiba
 *
 */
public interface Chain extends Entity {
	@PrimaryKey
	@NotNull
	@AutoIncrement
	public Integer getChain_ID();
	public Integer getPrefix01();
	public Integer getPrefix02();
	public Integer getSuffix();
	public void setPrefix01(Integer prefix01);
	public void setPrefix02(Integer prefix02);
	public void setSafix(Integer safix);

	/**
	 * 開始かどうか
	 */
	public Boolean getStart();
	public void setStart(Boolean start);
}
