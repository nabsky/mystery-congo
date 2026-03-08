package com.zorindisplays.host.infrastructure.db

import com.zorindisplays.host.app.AppConfig
import com.zorindisplays.host.infrastructure.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object DatabaseBootstrap {
    fun bootstrap(config: AppConfig) {
        transaction {
            // System state
            if (SystemStateTable.selectAll().empty()) {
                SystemStateTable.insert {
                    it[id] = 1
                    it[systemMode] = "ACCEPTING_BETS"
                    it[stateVersion] = 1L
                    it[lastEventId] = 0L
                    it[pendingWinId] = null
                    it[updatedAt] = Instant.now().toEpochMilli()
                }
            }

            if (ServerSettingsTable.selectAll().empty()) {
                ServerSettingsTable.insert {
                    it[id] = 1L
                    it[currencyCode] = "CFA"
                    it[baseBetAmount] = 100000L
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            // Table state
            for (tableId in 1..config.tableCount) {
                if (TableStateTable.selectAll()
                        .where { TableStateTable.tableId eq tableId }.empty()) {
                    TableStateTable.insert {
                        it[TableStateTable.tableId] = tableId
                        it[lastSeenAt] = null
                        it[updatedAt] = Instant.now().toEpochMilli()
                    }
                }
            }
            // Jackpot config
            val jackpotConfigs = listOf(
                mapOf(
                    "jackpotId" to "RUBY",
                    "resetAmount" to 100000L,
                    "contributionPerBet" to 100L,
                    "hitFrequencyGames" to 1000,
                    "priorityOrder" to 1,
                    "enabled" to true,
                    "updatedAt" to Instant.now().toEpochMilli()
                ),
                mapOf(
                    "jackpotId" to "GOLD",
                    "resetAmount" to 20000L,
                    "contributionPerBet" to 50L,
                    "hitFrequencyGames" to 300,
                    "priorityOrder" to 2,
                    "enabled" to true,
                    "updatedAt" to Instant.now().toEpochMilli()
                ),
                mapOf(
                    "jackpotId" to "JADE",
                    "resetAmount" to 5000L,
                    "contributionPerBet" to 10L,
                    "hitFrequencyGames" to 100,
                    "priorityOrder" to 3,
                    "enabled" to true,
                    "updatedAt" to Instant.now().toEpochMilli()
                )
            )
            for (cfg in jackpotConfigs) {
                if (JackpotConfigTable.selectAll()
                        .where { JackpotConfigTable.jackpotId eq cfg["jackpotId"] as String }.empty()) {
                    JackpotConfigTable.insert {
                        it[jackpotId] = cfg["jackpotId"] as String
                        it[resetAmount] = cfg["resetAmount"] as Long
                        it[contributionPerBet] = cfg["contributionPerBet"] as Long
                        it[hitFrequencyGames] = cfg["hitFrequencyGames"] as Int
                        it[priorityOrder] = cfg["priorityOrder"] as Int
                        it[enabled] = cfg["enabled"] as Boolean
                        it[updatedAt] = cfg["updatedAt"] as Long
                    }
                }
            }
            // Jackpot state
            for (cfg in jackpotConfigs) {
                val jackpotKey = cfg["jackpotId"] as String
                if (JackpotStateTable.selectAll()
                        .where { JackpotStateTable.jackpotId eq jackpotKey }.empty()) {
                    JackpotStateTable.insert {
                        it[JackpotStateTable.jackpotId] = jackpotKey
                        it[currentAmount] = cfg["resetAmount"] as Long
                        it[gamesSinceLastHit] = 0
                        it[updatedAt] = Instant.now().toEpochMilli()
                    }
                }
            }
        }
    }
}

