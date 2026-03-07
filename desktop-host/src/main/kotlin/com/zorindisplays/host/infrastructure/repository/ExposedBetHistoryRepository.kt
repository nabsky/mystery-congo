package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchItemTable
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import org.jetbrains.exposed.sql.insert

class ExposedBetHistoryRepository {

    suspend fun createBetBatch(
        tableId: Int,
        boxIds: List<Int>,
        result: String,
        winningJackpotId: String?,
        winningBoxId: Int?
    ): Long = dbQuery {
        val betBatchId = BetBatchTable.insert {
            it[BetBatchTable.tableId] = tableId
            it[confirmedAt] = System.currentTimeMillis()
            it[boxCount] = boxIds.size
            it[BetBatchTable.result] = result
            it[BetBatchTable.winningJackpotId] = winningJackpotId
            it[BetBatchTable.winningBoxId] = winningBoxId
        } get BetBatchTable.id

        betBatchId
    }

    suspend fun createBetBatchItems(
        betBatchId: Long,
        boxIds: List<Int>,
        winningBoxId: Int?
    ) = dbQuery {
        val hitIndex = if (winningBoxId == null) -1 else boxIds.indexOf(winningBoxId)

        boxIds.forEachIndexed { index, boxId ->
            val itemResult = when {
                winningBoxId == null -> "LOSE"
                index < hitIndex -> "LOSE"
                index == hitIndex -> "WIN"
                else -> "SKIPPED_AFTER_HIT"
            }

            BetBatchItemTable.insert {
                it[BetBatchItemTable.betBatchId] = betBatchId
                it[BetBatchItemTable.boxId] = boxId
                it[seqNo] = index
                it[result] = itemResult
            }
        }
    }
}