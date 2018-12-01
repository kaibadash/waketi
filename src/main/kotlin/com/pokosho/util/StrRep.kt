package com.pokosho.util

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Enumeration
import java.util.Hashtable
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.pokosho.PokoshoException

/**
 * 文字列変換.
 * @author kaiba
 */
class StrRep @Throws(PokoshoException::class)
constructor(repStrPath: String) {
    private val pattens: Hashtable<String, Pattern>

    init {
        pattens = Hashtable()
        var file: File? = null
        var filereader: FileReader? = null
        var br: BufferedReader? = null
        try {
            file = File(repStrPath)
            filereader = FileReader(file)
            br = BufferedReader(filereader)
            var line: String? = null
            while ((line = br.readLine()) != null) {
                log.debug("repstr line:" + line!!)
                addToPatters(line)
            }
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
            val mat = p.matcher(org)
            res = mat.replaceAll(key)
        }
        log.debug("replaced:$res")
        return res
    }

    private fun addToPatters(line: String) {
        val splited = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val targets = splited[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()

        sb.append("(" + targets[0] + ")")
        for (i in 1 until targets.size) {
            sb.append("|(" + targets[i] + ")")
        }
        log.debug("regex str:" + sb.toString())
        pattens[splited[0]] = Pattern.compile(sb.toString())
    }

    companion object {
        private val log = LoggerFactory.getLogger(StrRep::class.java)
    }
}
