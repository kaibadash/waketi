package com.pokosho.db

import com.pokosho.PokoshoException
import net.java.ao.EntityManager
import net.java.ao.builder.EntityManagerBuilder
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


object DBUtil {
    private var manager: EntityManager? = null
    private var dbUri: String? = null
    private var dbUser: String? = null
    private var dbPassword: String? = null
    private val KEY_DB_URI = "db.uri"
    private val KEY_DB_USER = "db.user"
    private val KEY_DB_PASSWORD = "db.password"

    val entityManager: EntityManager
        @Throws(PokoshoException::class)
        get() {
            if (manager == null) {
                throw PokoshoException("EntityManager was not loaded.")
            }
            return manager!!
        }

    @Throws(PokoshoException::class)
    fun getEntityManager(propPath: String): EntityManager {
        if (manager == null) {
            loadProp(propPath)

            manager = EntityManagerBuilder
                .url(dbUri)
                .username(dbUser)
                .password(dbPassword)
                .auto()
                .build()

            // ActiveObjects のロガーを取得
            val logger = Logger.getLogger("net.java.ao")
            // ログレベルの設定
            logger.level = Level.WARNING
        }
        return manager!!
    }

    @Throws(PokoshoException::class)
    private fun loadProp(propPath: String) {
        val prop = Properties()
        try {
            prop.load(FileInputStream(propPath))
            dbUri = prop.getProperty(KEY_DB_URI)
            dbPassword = prop.getProperty(KEY_DB_PASSWORD)
            dbUser = prop.getProperty(KEY_DB_USER)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            throw PokoshoException(e)
        } catch (e: IOException) {
            e.printStackTrace()
            throw PokoshoException(e)
        }
    }
}
