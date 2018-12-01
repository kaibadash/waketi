package com.pokosho.bot

import java.io.BufferedReader
import java.io.FileReader

import com.pokosho.PokoshoException

/**
 * ファイルパスから文字列を読み込み学習する基本的なBot.
 * @author kaiba
 */
class FileStudyBot @Throws(PokoshoException::class)
constructor(dbPropPath: String, botPropPath: String) : AbstractBot(dbPropPath, botPropPath) {

    @Throws(PokoshoException::class)
    override fun study(file: String) {
        try {
            FileReader(file).use { freader ->
                BufferedReader(freader).use { reader ->
                    var line: String? = null
                    do {
                        line = reader.readLine()
                        studyFromLine(line)
                    } while (line != null)
                }
            }
        } catch (e: Exception) {
            throw PokoshoException(e)
        }

    }
}
