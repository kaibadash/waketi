package com.pokosho.bot.twitter

import java.util.ArrayList
import java.util.LinkedHashMap

import net.arnx.jsonic.JSON

class Trend {
    var trends: LinkedHashMap<String, ArrayList<ATrend>>? = null
    var as_of: Long = 0

    init {
        trends = LinkedHashMap()
    }

    fun addTrend(key: String, name: String, events: String, promotedContent: String, query: String) {
        var trendList: ArrayList<ATrend>? = this.trends!![key]
        if (trendList == null) trendList = ArrayList()
        trendList.add(ATrend(name, events, promotedContent, query))
        this.trends!![key] = trendList
    }

    inner class ATrend {
        var name: String? = null
        var events: String? = null
        var promoted_content: String? = null
        var query: String? = null

        constructor() {

        }

        constructor(name: String, events: String, promotedContent: String, query: String) {
            this.name = name
            this.events = events
            this.promoted_content = promotedContent
            this.query = query
        }
    }

    companion object {

        // 動作確認
        @JvmStatic
        fun main(args: Array<String>) {
            // decode
            val jsonStr =
                "{ \"trends\": { \"2011-01-14 15:20\": [{\"name\": \"#confessionhour\",\"events\": null,\"promoted_content\": null,\"query\": \"#confessionhour\"},{\"name\": \"#thankssuju\",\"events\": null,\"promoted_content\": null,\"query\": \"#thankssuju\"}],\"2011-01-14 04:20\": [{\"name\": \"#nowthatslove\",\"events\": null,\"promoted_content\": null,\"query\": \"#nowthatslove\"},{\"name\": \"#imfromphilly\",\"events\": null,\"promoted_content\": null,\"query\": \"#imfromphilly\"}]},\"as_of\": 1295039871}"
            val trend = JSON.decode(jsonStr, Trend::class.java)
            val keys = trend.trends!!.keys
            for (key in keys) {
                println("date:$key")
                val trends = trend.trends!![key]
                for (t in trends) {
                    println("name:" + t.name!!)
                }
            }
            println("asof:" + trend.as_of)
            // encode
            val trend2 = Trend()
            trend2.as_of = System.currentTimeMillis()
            trend2.addTrend("20111029", "hoge", "hoge", "hoge", "hoge")
            trend2.addTrend("20111029", "muga", "muga", "muga", "muga")
            trend2.addTrend("20111029", "uryy", "uryy", "uryy", "uryy")
            val encodedJSONStr = JSON.encode(trend2)
            println(encodedJSONStr)
        }
    }
}