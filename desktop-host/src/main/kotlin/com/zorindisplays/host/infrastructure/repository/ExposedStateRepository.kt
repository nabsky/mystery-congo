package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.model.HostState
import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.JackpotState
import com.zorindisplays.host.domain.model.PendingWin
import com.zorindisplays.host.domain.model.SystemMode
import com.zorindisplays.host.domain.model.TableState
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedStateRepository {

    suspend fun getCurrentState(): HostState? = dbQuery {
        val systemRow = SystemStateTable.selectAll().singleOrNull() ?: return@dbQuery null
        val now = Instant.now().toEpochMilli()

        val systemMode = SystemMode.valueOf(systemRow[SystemStateTable.systemMode])
        val stateVersion = systemRow[SystemStateTable.stateVersion]
        val lastEventId = systemRow[SystemStateTable.lastEventId]
        val pendingWinId = systemRow[SystemStateTable.pendingWinId]

        val jackpots: Map<JackpotId, JackpotState> =
            JackpotStateTable.selectAll().associate { row ->
                val jackpotId = JackpotId.valueOf(row[JackpotStateTable.jackpotId])
                jackpotId to JackpotState(
                    jackpotId = jackpotId,
                    currentAmount = row[JackpotStateTable.currentAmount],
                    gamesSinceLastHit = row[JackpotStateTable.gamesSinceLastHit]
                )
            }

        val tables: List<TableState> = TableStateTable
            .selectAll()
            .orderBy(TableStateTable.tableId)
            .map { row ->
            val tableId = row[TableStateTable.tableId]

            val activeBoxes = TableActiveBoxTable
                .selectAll()
                .where { TableActiveBoxTable.tableId eq tableId }
                .map { it[TableActiveBoxTable.boxId] }
                .toSet()

            val recentBoxes = TableRecentBoxTable
                .selectAll()
                .where {
                    (TableRecentBoxTable.tableId eq tableId) and
                            (TableRecentBoxTable.expiresAt greater now)
                }
                .map { it[TableRecentBoxTable.boxId] }
                .toSet()

            val lastSeenAt = row[TableStateTable.lastSeenAt]

            TableState(
                tableId = tableId,
                activeBoxes = activeBoxes,
                recentBoxes = recentBoxes,
                isActive = lastSeenAt?.let { now - it < 60_000L } ?: false,
                lastSeenAt = lastSeenAt
            )
        }

        val pendingWin = pendingWinId?.let { id ->
            PendingWinTable
                .selectAll()
                .where { PendingWinTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    PendingWin(
                        id = row[PendingWinTable.id],
                        jackpotId = JackpotId.valueOf(row[PendingWinTable.jackpotId]),
                        tableId = row[PendingWinTable.tableId],
                        winningBoxId = row[PendingWinTable.winningBoxId],
                        dealerConfirmed = row[PendingWinTable.dealerConfirmed],
                        winAmount = row[PendingWinTable.winAmount],
                        createdAt = row[PendingWinTable.createdAt]
                    )
                }
        }

        HostState(
            stateVersion = stateVersion,
            lastEventId = lastEventId,
            systemMode = systemMode,
            pendingWin = pendingWin,
            jackpots = jackpots,
            tables = tables
        )
    }

    suspend fun initializeIfEmpty() = dbQuery {
        if (SystemStateTable.selectAll().empty()) {
            SystemStateTable.insert {
                it[id] = 1
                it[systemMode] = SystemMode.ACCEPTING_BETS.name
                it[stateVersion] = 1L
                it[lastEventId] = 0L
                it[pendingWinId] = null
                it[updatedAt] = Instant.now().toEpochMilli()
            }
        }
    }

    suspend fun updateSystemMode(mode: SystemMode) = dbQuery {
        SystemStateTable.update({ SystemStateTable.id eq 1 }) {
            it[systemMode] = mode.name
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    suspend fun updateStateVersionAndLastEvent(stateVersion: Long, lastEventId: Long) = dbQuery {
        SystemStateTable.update({ SystemStateTable.id eq 1 }) {
            it[SystemStateTable.stateVersion] = stateVersion
            it[SystemStateTable.lastEventId] = lastEventId
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }
}