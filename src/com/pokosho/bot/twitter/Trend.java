package com.pokosho.bot.twitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import net.arnx.jsonic.JSON;

public class Trend {
	private LinkedHashMap<String, ArrayList<ATrend>> trends;
	private long asOf;

	public Trend() {
		trends = new LinkedHashMap<String, ArrayList<ATrend>>();
	}

	public LinkedHashMap<String, ArrayList<ATrend>> getTrends() {
		return trends;
	}

	public void setTrends(LinkedHashMap<String, ArrayList<ATrend>> trends) {
		this.trends = trends;
	}

	public long getAsOf() {
		return asOf;
	}

	public void setAsOf(long asOf) {
		this.asOf = asOf;
	}

	public void addTrend(String key, String name, String events, String promotedContent, String query) {
		ArrayList<ATrend> trendList = this.trends.get(key);
		if (trendList == null) trendList = new ArrayList<Trend.ATrend>();
		trendList.add(new ATrend(name, events, promotedContent, query));
		this.trends.put(key, trendList);
	}

	public class ATrend {
		private String name;
		private String events;
		private String promotedContent;
		private String query;

		public ATrend() {

		}

		public ATrend(String name, String events, String promotedContent, String query) {
			this.name = name;
			this.events = events;
			this.promotedContent = promotedContent;
			this.query = query;
		}

		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getEvents() {
			return events;
		}
		public void setEvents(String events) {
			this.events = events;
		}
		public String getPromotedContent() {
			return promotedContent;
		}
		public void setPromotedContent(String promotedContent) {
			this.promotedContent = promotedContent;
		}
		public String getQuery() {
			return query;
		}
		public void setQuery(String query) {
			this.query = query;
		}
	}

	// 動作確認
	public static void main(String[] args) {
		// decode
		String jsonStr = "{ \"trends\": { \"2011-01-14 15:20\": [{\"name\": \"#confessionhour\",\"events\": null,\"promoted_content\": null,\"query\": \"#confessionhour\"},{\"name\": \"#thankssuju\",\"events\": null,\"promoted_content\": null,\"query\": \"#thankssuju\"}],\"2011-01-14 04:20\": [{\"name\": \"#nowthatslove\",\"events\": null,\"promoted_content\": null,\"query\": \"#nowthatslove\"},{\"name\": \"#imfromphilly\",\"events\": null,\"promoted_content\": null,\"query\": \"#imfromphilly\"}]},\"as_of\": 1295039871}";
		Trend trend = JSON.decode(jsonStr, Trend.class);
		Set<String> keys = trend.getTrends().keySet();
		for (Object key : keys) {
			System.out.println("date:" + key);
			List<ATrend> trends = trend.getTrends().get(key);
			for (ATrend t : trends) {
				System.out.println("name:" + t.getName());
			}
		}
		System.out.println("asof:" + trend.getAsOf());
		// encode
		Trend trend2 = new Trend();
		trend2.setAsOf(System.currentTimeMillis());
		trend2.addTrend("20111029", "hoge", "hoge", "hoge", "hoge");
		trend2.addTrend("20111029", "muga", "muga", "muga", "muga");
		trend2.addTrend("20111029", "uryy", "uryy", "uryy", "uryy");
		String encodedJSONStr = JSON.encode(trend2);
		System.out.println(encodedJSONStr);
	}
}