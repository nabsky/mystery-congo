package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedJackpotHitRepository {

    suspend fun createHit(
        jackpotId: String,
        tableId: Int,
        triggerBoxId: Int,
        winAmount: Long,
        betBatchId: Long?
    ): Long = dbQuery {
        val hitId = JackpotHitTable.insert {
            it[JackpotHitTable.jackpotId] = jackpotId
            it[JackpotHitTable.tableId] = tableId
            it[JackpotHitTable.triggerBoxId] = triggerBoxId
            it[JackpotHitTable.winAmount] = winAmount
            it[JackpotHitTable.betBatchId] = betBatchId
            it[hitAt] = System.currentTimeMillis()
            it[payoutConfirmedAt] = null
            it[confirmedBoxId] = null
            it[status] = "PENDING_CONFIRM"
        } get JackpotHitTable.id

        hitId
    }

    suspend fun confirmLatestPendingHit(
        jackpotId: String,
        tableId: Int,
        confirmedBoxId: Int
    ) = dbQuery {
        val row = JackpotHitTable.selectAll()
            .where {
                (JackpotHitTable.jackpotId eq jackpotId) and
                        (JackpotHitTable.tableId eq tableId) and
                        (JackpotHitTable.status eq "PENDING_CONFIRM")
            }
            .orderBy(JackpotHitTable.id to SortOrder.ASC)
            .lastOrNull()
            ?: return@dbQuery

        JackpotHitTable.update({ JackpotHitTable.id eq row[JackpotHitTable.id] }) {
            it[payoutConfirmedAt] = System.currentTimeMillis()
            it[JackpotHitTable.confirmedBoxId] = confirmedBoxId
            it[status] = "CONFIRMED"
        }
    }
}