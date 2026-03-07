package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.PendingWin
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedPendingWinRepository {

    suspend fun getPendingWin(): PendingWin? = dbQuery {
        val pendingWinId = SystemStateTable
            .selectAll()
            .singleOrNull()
            ?.get(SystemStateTable.pendingWinId)
            ?: return@dbQuery null

        PendingWinTable.selectAll()
            .where { PendingWinTable.id eq pendingWinId }
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

    suspend fun savePendingWin(pendingWin: PendingWin): Long = dbQuery {
        val pendingWinId = PendingWinTable.insert {
            it[jackpotId] = pendingWin.jackpotId.name
            it[tableId] = pendingWin.tableId
            it[winningBoxId] = pendingWin.winningBoxId
            it[dealerConfirmed] = pendingWin.dealerConfirmed
            it[winAmount] = pendingWin.winAmount
            it[status] = "WAITING_CONFIRM"
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        } get PendingWinTable.id

        SystemStateTable.update({ SystemStateTable.id eq 1 }) {
            it[SystemStateTable.pendingWinId] = pendingWinId
            it[updatedAt] = System.currentTimeMillis()
        }

        pendingWinId
    }

    suspend fun markDealerConfirmed(pendingWinId: Long) = dbQuery {
        PendingWinTable.update({ PendingWinTable.id eq pendingWinId }) {
            it[dealerConfirmed] = true
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun clearPendingWin() = dbQuery {
        SystemStateTable.update({ SystemStateTable.id eq 1 }) {
            it[pendingWinId] = null
            it[updatedAt] = System.currentTimeMillis()
        }
    }
}