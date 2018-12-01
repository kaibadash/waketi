package com.pokosho.dao

import net.java.ao.Entity
import net.java.ao.schema.NotNull
import net.java.ao.schema.PrimaryKey

interface Reply : Entity {
    @get:PrimaryKey
    @get:NotNull
    var user_ID: Long?

    var tweet_ID: Long?
    var time: Int?
}
