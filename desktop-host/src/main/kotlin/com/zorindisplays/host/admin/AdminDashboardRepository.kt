package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll

class AdminDashboardRepository(
    private val tableCount: Int
) {
    private val serverSettingsRepository = AdminServerSettingsRepository()
    suspend fun getDashboard(): AdminDashboardDto {
        val currencyCode = serverSettingsRepository.getSettings().currencyCode
        val baseBetAmount = serverSettingsRepository.getSettings().baseBetAmount
        return dbQuery {
            val now = System.currentTimeMillis()
            val startOfDay = now - (now % 86_400_000L)

            val systemRow = SystemStateTable.selectAll().single()

            val jackpots = JackpotStateTable.selectAll()
                .associate { row ->
                    row[JackpotStateTable.jackpotId] to row[JackpotStateTable.currentAmount]
                }

            val activeTablesCount = TableStateTable.selectAll()
                .count { row ->
                    val lastSeenAt = row[TableStateTable.lastSeenAt]
                    lastSeenAt != null && now - lastSeenAt < 60_000L
                }

            val pendingWinId = systemRow[SystemStateTable.pendingWinId]
            val pendingWin = pendingWinId?.let { id ->
                PendingWinTable.selectAll()
                    .firstOrNull { it[PendingWinTable.id] == id }
                    ?.let { row ->
                        AdminDashboardPendingWinDto(
                            jackpotId = row[PendingWinTable.jackpotId],
                            tableId = row[PendingWinTable.tableId],
                            winningBoxId = row[PendingWinTable.winningBoxId],
                            dealerConfirmed = row[PendingWinTable.dealerConfirmed],
                            winAmount = row[PendingWinTable.winAmount]
                        )
                    }
            }

            val latestHit = JackpotHitTable.selectAll()
                .orderBy(JackpotHitTable.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.let { row ->
                    AdminDashboardLatestHitDto(
                        jackpotId = row[JackpotHitTable.jackpotId],
                        tableId = row[JackpotHitTable.tableId],
                        triggerBoxId = row[JackpotHitTable.triggerBoxId],
                        winAmount = row[JackpotHitTable.winAmount],
                        hitAt = row[JackpotHitTable.hitAt],
                        status = row[JackpotHitTable.status]
                    )
                }

            val latestBatches = BetBatchTable.selectAll()
                .orderBy(BetBatchTable.id to SortOrder.DESC)
                .limit(10)
                .map { row ->
                    AdminDashboardLatestBatchDto(
                        id = row[BetBatchTable.id],
                        tableId = row[BetBatchTable.tableId],
                        confirmedAt = row[BetBatchTable.confirmedAt],
                        result = row[BetBatchTable.result],
                        winningJackpotId = row[BetBatchTable.winningJackpotId],
                        winningBoxId = row[BetBatchTable.winningBoxId]
                    )
                }

            val totalBoxesAllTime = BetBatchTable.selectAll()
                .sumOf { row -> row[BetBatchTable.boxCount].toLong() }

            val totalBoxesToday = BetBatchTable.selectAll()
                .filter { row -> row[BetBatchTable.confirmedAt] >= startOfDay }
                .sumOf { row -> row[BetBatchTable.boxCount].toLong() }

            val totalInAllTime = totalBoxesAllTime * baseBetAmount
            val totalInToday = totalBoxesToday * baseBetAmount

            val totalOutAllTime = JackpotHitTable.selectAll()
                .filter { row -> row[JackpotHitTable.status] == "CONFIRMED" }
                .sumOf { row -> row[JackpotHitTable.winAmount] }

            val totalOutToday = JackpotHitTable.selectAll()
                .filter { row ->
                    row[JackpotHitTable.status] == "CONFIRMED" &&
                            (row[JackpotHitTable.payoutConfirmedAt] ?: 0L) >= startOfDay
                }
                .sumOf { row -> row[JackpotHitTable.winAmount] }

            val totalBatchesToday = BetBatchTable.selectAll()
                .count { row -> row[BetBatchTable.confirmedAt] >= startOfDay }
                .toLong()

            val totalHitsToday = JackpotHitTable.selectAll()
                .count { row -> row[JackpotHitTable.hitAt] >= startOfDay }
                .toLong()

            AdminDashboardDto(
                systemMode = systemRow[SystemStateTable.systemMode],
                pendingWin = pendingWin,
                jackpots = jackpots,
                activeTablesCount = activeTablesCount,
                totalTables = tableCount,
                totalBatchesToday = totalBatchesToday,
                totalHitsToday = totalHitsToday,
                latestHit = latestHit,
                latestBatches = latestBatches,
                totalInAllTime = totalInAllTime,
                totalOutAllTime = totalOutAllTime,
                totalInToday = totalInToday,
                totalOutToday = totalOutToday,
                currencyCode = currencyCode,
            )
        }
    }
}