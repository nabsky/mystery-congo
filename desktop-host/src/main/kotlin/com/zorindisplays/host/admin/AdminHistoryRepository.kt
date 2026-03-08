package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchItemTable
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

class AdminHistoryRepository {

    suspend fun getBetBatches(
        limit: Int = 50,
        tableId: Int? = null,
        result: String? = null,
        beforeId: Long? = null
    ): List<AdminBetBatchDto> = dbQuery {
        var condition: Op<Boolean>? = null

        if (tableId != null) {
            condition = (condition?.and(BetBatchTable.tableId eq tableId)) ?: (BetBatchTable.tableId eq tableId)
        }
        if (result != null) {
            condition = (condition?.and(BetBatchTable.result eq result)) ?: (BetBatchTable.result eq result)
        }
        if (beforeId != null) {
            condition = (condition?.and(BetBatchTable.id less beforeId)) ?: (BetBatchTable.id less beforeId)
        }

        val query = if (condition == null) {
            BetBatchTable.selectAll()
        } else {
            BetBatchTable.selectAll().where { condition }
        }

        val batchRows = query
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
            val items = itemsByBatchId[batchId]
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
            val boxIds = items
                .sortedBy { it.seqNo }
                .map { it.boxId }
            AdminBetBatchDto(
                id = batchId,
                tableId = row[BetBatchTable.tableId],
                confirmedAt = row[BetBatchTable.confirmedAt],
                boxCount = row[BetBatchTable.boxCount],
                result = row[BetBatchTable.result],
                winningJackpotId = row[BetBatchTable.winningJackpotId],
                winningBoxId = row[BetBatchTable.winningBoxId],
                boxIds = boxIds,
                items = items
            )
        }
    }

    suspend fun getJackpotHits(
        limit: Int = 50,
        tableId: Int? = null,
        jackpotId: String? = null,
        status: String? = null,
        beforeId: Long? = null
    ): List<AdminJackpotHitDto> = dbQuery {
        var condition: Op<Boolean>? = null

        if (tableId != null) {
            condition = (condition?.and(JackpotHitTable.tableId eq tableId)) ?: (JackpotHitTable.tableId eq tableId)
        }
        if (jackpotId != null) {
            condition = (condition?.and(JackpotHitTable.jackpotId eq jackpotId)) ?: (JackpotHitTable.jackpotId eq jackpotId)
        }
        if (status != null) {
            condition = (condition?.and(JackpotHitTable.status eq status)) ?: (JackpotHitTable.status eq status)
        }
        if (beforeId != null) {
            condition = (condition?.and(JackpotHitTable.id less beforeId)) ?: (JackpotHitTable.id less beforeId)
        }

        val query = if (condition == null) {
            JackpotHitTable.selectAll()
        } else {
            JackpotHitTable.selectAll().where { condition }
        }

        query
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

    suspend fun getPendingWins(
        tableId: Int? = null,
        dealerConfirmed: Boolean? = null
    ): List<AdminPendingWinDto> = dbQuery {
        var condition: Op<Boolean>? = null

        if (tableId != null) {
            condition = (condition?.and(PendingWinTable.tableId eq tableId)) ?: (PendingWinTable.tableId eq tableId)
        }
        if (dealerConfirmed != null) {
            condition = (condition?.and(PendingWinTable.dealerConfirmed eq dealerConfirmed))
                ?: (PendingWinTable.dealerConfirmed eq dealerConfirmed)
        }

        val query = if (condition == null) {
            PendingWinTable.selectAll()
        } else {
            PendingWinTable.selectAll().where { condition }
        }

        query
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