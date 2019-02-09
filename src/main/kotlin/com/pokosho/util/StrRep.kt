package com.pokosho.util

import com.pokosho.PokoshoException
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * 文字列変換.
 * @author kaiba
 */
class StrRep @Throws(PokoshoException::class)
constructor(repStrPath: String) {
    private val pattens: Hashtable<String, Pattern> = Hashtable()

    init {
        var filereader: FileReader? = null
        var br: BufferedReader? = null
        try {
            val file = File(repStrPath)
            filereader = FileReader(file)
            br = BufferedReader(filereader)

            var line: String?
            do {
                line = br.readLine()
                if (line == null) break
                log.debug("repstr line:" + line)
                addToPatters(line)
            } while (line != null)
        } catch (e: IOException) {
            throw PokoshoException(e)
        } finally {
            try {
                br?.close()
                filereader?.close()
            } catch (e: Exception) {
                throw PokoshoException(e)
            }

        }
    }

    fun rep(org: String): String {
        val keys = pattens.keys()
        var res = org
        log.debug("original:$org")
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            val p = pattens[key]
            val mat = p!!.matcher(org)
            res = mat.replaceAll(key)
        }
        log.debug("replaced:$res")
        return res
    }

    private fun addToPatters(line: String) {
        val split = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val targets = split[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()

        sb.append("(" + targets[0] + ")")
        for (i in 1 until targets.size) {
            sb.append("|(" + targets[i] + ")")
        }
        log.debug("regex str:" + sb.toString())
        pattens[split[0]] = Pattern.compile(sb.toString())
    }

    companion object {
        private val log = LoggerFactory.getLogger(StrRep::class.java)
    }
}
