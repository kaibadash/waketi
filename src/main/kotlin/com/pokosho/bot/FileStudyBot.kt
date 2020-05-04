package com.pokosho.bot

import com.pokosho.PokoshoException
import java.io.BufferedReader
import java.io.FileReader

/**
 * ファイルパスから文字列を読み込み学習する基本的なBot.
 * @author kaiba
 */
class FileStudyBot @Throws(PokoshoException::class)
constructor(botPropPath: String) : AbstractBot(botPropPath) {

    @Throws(PokoshoException::class)
    override fun study(str: String?) {
        try {
            FileReader(str).use { fileReader ->
                BufferedReader(fileReader).use { reader ->
                    var message: String?
                    do {
                        message = reader.readLine()
                        studyFromMessage(message)
                    } while (message != null)
                }
            }
        } catch (e: Exception) {
            throw PokoshoException(e)
        }

    }
}
