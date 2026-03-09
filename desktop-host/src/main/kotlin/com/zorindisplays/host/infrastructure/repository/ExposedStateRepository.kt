package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.model.HostState
import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.JackpotState
import com.zorindisplays.host.domain.model.PendingWin
import com.zorindisplays.host.domain.model.SystemMode
import com.zorindisplays.host.domain.model.TableState
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.DevicePresenceTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotConfigTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.ServerSettingsTable
import com.zorindisplays.host.infrastructure.db.tables.SyncEventTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

class ExposedStateRepository {

    companion object {
        private const val DEVICE_ONLINE_TTL_MS = 15_000L
    }

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

        val onlineTables = DevicePresenceTable
            .selectAll()
            .mapNotNull { row ->
                val lastSeen = row[DevicePresenceTable.lastSeenAt]
                val tableId = row[DevicePresenceTable.tableId]

                if (
                    row[DevicePresenceTable.deviceType] == "TABLE" &&
                    tableId != null &&
                    now - lastSeen < DEVICE_ONLINE_TTL_MS
                ) {
                    tableId
                } else {
                    null
                }
            }
            .toSet()

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
                    isActive = tableId in onlineTables,
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

        val currencyCode = ServerSettingsTable
            .selectAll()
            .single()[ServerSettingsTable.currencyCode]

        val startOfToday = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val jackpotGrowth = JackpotConfigTable
            .selectAll()
            .associate { cfgRow ->
                val jackpotId = JackpotId.valueOf(cfgRow[JackpotConfigTable.jackpotId])
                val contributionPerBet = cfgRow[JackpotConfigTable.contributionPerBet]

                val lastConfirmedPayoutToday = JackpotHitTable
                    .selectAll()
                    .where {
                        (JackpotHitTable.jackpotId eq jackpotId.name) and
                                (JackpotHitTable.status eq "CONFIRMED") and
                                (JackpotHitTable.payoutConfirmedAt greaterEq startOfToday)
                    }
                    .orderBy(JackpotHitTable.payoutConfirmedAt, org.jetbrains.exposed.sql.SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()

                val sinceTs = lastConfirmedPayoutToday?.get(JackpotHitTable.payoutConfirmedAt) ?: startOfToday

                val confirmedBoxesSinceTs = BetBatchTable
                    .selectAll()
                    .where { BetBatchTable.confirmedAt greaterEq sinceTs }
                    .sumOf { row -> row[BetBatchTable.boxCount].toLong() }

                jackpotId to (confirmedBoxesSinceTs * contributionPerBet)
            }

        HostState(
            stateVersion = stateVersion,
            lastEventId = lastEventId,
            systemMode = systemMode,
            pendingWin = pendingWin,
            jackpots = jackpots,
            jackpotGrowth = jackpotGrowth,
            tables = tables,
            currencyCode = currencyCode
        )
    }
}