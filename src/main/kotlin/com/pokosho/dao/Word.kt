package com.pokosho.dao

import net.java.ao.Entity
import net.java.ao.schema.AutoIncrement
import net.java.ao.schema.NotNull
import net.java.ao.schema.PrimaryKey

interface Word : Entity {
    @get:PrimaryKey
    @get:NotNull
    @get:AutoIncrement
    val word_ID: Int?
    @get:NotNull
    var pos_ID: Int?
    @get:NotNull
    var word: String

    var word_Count: Int?
    var time: Int?
}