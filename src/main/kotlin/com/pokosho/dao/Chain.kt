package com.pokosho.dao

import net.java.ao.Entity
import net.java.ao.RawEntity
import net.java.ao.schema.AutoIncrement
import net.java.ao.schema.NotNull
import net.java.ao.schema.PrimaryKey

/**
 * 三階のマルコフ.
 * @author kaiba
 */
interface Chain : RawEntity<Int> {
    @get:PrimaryKey
    @get:NotNull
    @get:AutoIncrement
    val chain_ID: Int?
    var prefix01: Int?
    var prefix02: Int?
    val suffix: Int?

    /**
     * 開始かどうか
     */
    var start: Boolean?
}
