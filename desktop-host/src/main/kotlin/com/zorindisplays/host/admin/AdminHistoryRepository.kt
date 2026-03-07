package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchItemTable
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

class AdminHistoryRepository {

    suspend fun getBetBatches(limit: Int = 50): List<AdminBetBatchDto> = dbQuery {
        val batchRows = BetBatchTable.selectAll()
            .orderBy(BetBatchTable.id to SortOrder.DESC)
            .limit(limit)
            .toList()

        val batchIds = batchRows.map { it[BetBatchTable.id] }

        val itemsByBatchId = if (batchIds.isEmpty()) {
            emptyMap()
        } else {
            BetBatchItemTable.selectAll()
                .toList()
                .filter { it[BetBatchItemTable.betBatchId] in batchIds }
                .groupBy { it[BetBatchItemTable.betBatchId] }
        }

        batchRows.map { row ->
            val batchId = row[BetBatchTable.id]
            AdminBetBatchDto(
                id = batchId,
                tableId = row[BetBatchTable.tableId],
                confirmedAt = row[BetBatchTable.confirmedAt],
                boxCount = row[BetBatchTable.boxCount],
                result = row[BetBatchTable.result],
                winningJackpotId = row[BetBatchTable.winningJackpotId],
                winningBoxId = row[BetBatchTable.winningBoxId],
                items = itemsByBatchId[batchId]
                    .orEmpty()
                    .sortedBy { it[BetBatchItemTable.seqNo] }
                    .map { item ->
                        AdminBetBatchItemDto(
                            id = item[BetBatchItemTable.id],
                            betBatchId = item[BetBatchItemTable.betBatchId],
                            boxId = item[BetBatchItemTable.boxId],
                            seqNo = item[BetBatchItemTable.seqNo],
                            result = item[BetBatchItemTable.result]
                        )
                    }
            )
        }
    }

    suspend fun getJackpotHits(limit: Int = 50): List<AdminJackpotHitDto> = dbQuery {
        JackpotHitTable.selectAll()
            .orderBy(JackpotHitTable.id to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AdminJackpotHitDto(
                    id = row[JackpotHitTable.id],
                    jackpotId = row[JackpotHitTable.jackpotId],
                    tableId = row[JackpotHitTable.tableId],
                    triggerBoxId = row[JackpotHitTable.triggerBoxId],
                    winAmount = row[JackpotHitTable.winAmount],
                    betBatchId = row[JackpotHitTable.betBatchId],
                    hitAt = row[JackpotHitTable.hitAt],
                    payoutConfirmedAt = row[JackpotHitTable.payoutConfirmedAt],
                    confirmedBoxId = row[JackpotHitTable.confirmedBoxId],
                    status = row[JackpotHitTable.status]
                )
            }
    }

    suspend fun getPendingWins(): List<AdminPendingWinDto> = dbQuery {
        PendingWinTable.selectAll()
            .orderBy(PendingWinTable.id to SortOrder.DESC)
            .map { row ->
                AdminPendingWinDto(
                    id = row[PendingWinTable.id],
                    jackpotId = row[PendingWinTable.jackpotId],
                    tableId = row[PendingWinTable.tableId],
                    winningBoxId = row[PendingWinTable.winningBoxId],
                    dealerConfirmed = row[PendingWinTable.dealerConfirmed],
                    winAmount = row[PendingWinTable.winAmount],
                    status = row[PendingWinTable.status],
                    createdAt = row[PendingWinTable.createdAt],
                    updatedAt = row[PendingWinTable.updatedAt]
                )
            }
    }
}