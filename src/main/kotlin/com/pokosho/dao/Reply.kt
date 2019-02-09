package com.pokosho.dao

import net.java.ao.RawEntity
import net.java.ao.schema.NotNull
import net.java.ao.schema.PrimaryKey

interface Reply : RawEntity<Int> {
    @get:PrimaryKey
    @get:NotNull
    var user_ID: Long?

    var tweet_ID: Long?
    var time: Int?
}
