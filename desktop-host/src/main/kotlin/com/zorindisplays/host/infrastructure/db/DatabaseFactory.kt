package com.zorindisplays.host.infrastructure.db

import com.zorindisplays.host.app.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import java.sql.SQLException

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun init(config: AppConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.jdbcUser
            password = config.jdbcPassword
            maximumPoolSize = config.dbPoolMaxSize
            minimumIdle = config.dbPoolMinIdle
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
            // TODO: вынести настройки в конфиг при необходимости
        }
        dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource!!)
    }

    fun createSchema(vararg tables: org.jetbrains.exposed.sql.Table) {
        try {
            org.jetbrains.exposed.sql.transactions.transaction {
                SchemaUtils.createMissingTablesAndColumns(*tables)
                // TODO: Для production использовать Flyway/Liquibase
            }
        } catch (e: SQLException) {
            throw RuntimeException("Failed to create schema", e)
        }
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}

