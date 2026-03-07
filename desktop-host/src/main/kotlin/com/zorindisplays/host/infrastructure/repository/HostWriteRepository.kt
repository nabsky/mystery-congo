package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.domain.model.JackpotConfig
import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.JackpotState
import com.zorindisplays.host.domain.model.PendingWin
import com.zorindisplays.host.domain.model.SystemMode
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.BetBatchItemTable
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotConfigTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SyncEventTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class HostWriteRepository {

    data class ConfirmBetsResult(
        val stateVersion: Long,
        val lastEventId: Long
    )

    suspend fun confirmBetsTransactional(
        tableId: Int,
        recentBoxTtlMs: Long,
        randomRoll: (Int) -> Int
    ): ConfirmBetsResult = dbQuery {
        val systemRow = SystemStateTable.selectAll().single()
        val systemMode = SystemMode.valueOf(systemRow[SystemStateTable.systemMode])
        require(systemMode == SystemMode.ACCEPTING_BETS) { "System is not accepting bets" }

        val currentStateVersion = systemRow[SystemStateTable.stateVersion]
        val now = System.currentTimeMillis()

        val activeBoxes = TableActiveBoxTable.selectAll()
            .where { TableActiveBoxTable.tableId eq tableId }
            .map { it[TableActiveBoxTable.boxId] }
            .sorted()

        if (activeBoxes.isEmpty()) {
            return@dbQuery ConfirmBetsResult(
                stateVersion = currentStateVersion,
                lastEventId = systemRow[SystemStateTable.lastEventId]
            )
        }

        TableStateTable.update({ TableStateTable.tableId eq tableId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }

        val jackpotConfigs = JackpotConfigTable.selectAll()
            .map { row ->
                JackpotConfig(
                    jackpotId = JackpotId.valueOf(row[JackpotConfigTable.jackpotId]),
                    resetAmount = row[JackpotConfigTable.resetAmount],
                    contributionPerBet = row[JackpotConfigTable.contributionPerBet],
                    hitFrequencyGames = row[JackpotConfigTable.hitFrequencyGames],
                    priorityOrder = row[JackpotConfigTable.priorityOrder],
                    enabled = row[JackpotConfigTable.enabled]
                )
            }
            .filter { it.enabled }
            .sortedBy { it.priorityOrder }

        val jackpotStates = JackpotStateTable.selectAll()
            .associate { row ->
                val jackpotId = JackpotId.valueOf(row[JackpotStateTable.jackpotId])
                jackpotId to JackpotState(
                    jackpotId = jackpotId,
                    currentAmount = row[JackpotStateTable.currentAmount],
                    gamesSinceLastHit = row[JackpotStateTable.gamesSinceLastHit]
                )
            }
            .toMutableMap()

        var pendingWin: PendingWin? = null

        activeBoxesLoop@ for (boxId in activeBoxes) {
            jackpotConfigs.forEach { cfg ->
                val current = jackpotStates.getValue(cfg.jackpotId)
                jackpotStates[cfg.jackpotId] = current.copy(
                    currentAmount = current.currentAmount + cfg.contributionPerBet,
                    gamesSinceLastHit = current.gamesSinceLastHit + 1
                )
            }

            for (cfg in jackpotConfigs) {
                val roll = randomRoll(cfg.hitFrequencyGames)
                if (roll == 0) {
                    val hitState = jackpotStates.getValue(cfg.jackpotId)
                    jackpotStates[cfg.jackpotId] = hitState.copy(gamesSinceLastHit = 0)

                    pendingWin = PendingWin(
                        jackpotId = cfg.jackpotId,
                        tableId = tableId,
                        winningBoxId = boxId,
                        dealerConfirmed = false,
                        winAmount = hitState.currentAmount,
                        createdAt = now
                    )
                    break@activeBoxesLoop
                }
            }
        }

        jackpotStates.values.forEach { state ->
            JackpotStateTable.update({ JackpotStateTable.jackpotId eq state.jackpotId.name }) {
                it[currentAmount] = state.currentAmount
                it[gamesSinceLastHit] = state.gamesSinceLastHit
                it[updatedAt] = now
            }
        }

        activeBoxes.forEach { boxId ->
            TableRecentBoxTable.deleteWhere {
                (TableRecentBoxTable.tableId eq tableId) and (TableRecentBoxTable.boxId eq boxId)
            }
            TableRecentBoxTable.insert {
                it[TableRecentBoxTable.tableId] = tableId
                it[TableRecentBoxTable.boxId] = boxId
                it[confirmedAt] = now
                it[expiresAt] = now + recentBoxTtlMs
            }
        }

        TableActiveBoxTable.deleteWhere { TableActiveBoxTable.tableId eq tableId }

        val betBatchId = BetBatchTable.insert {
            it[BetBatchTable.tableId] = tableId
            it[confirmedAt] = now
            it[boxCount] = activeBoxes.size
            it[result] = if (pendingWin == null) "NO_WIN" else "JACKPOT_HIT"
            it[winningJackpotId] = pendingWin?.jackpotId?.name
            it[winningBoxId] = pendingWin?.winningBoxId
        } get BetBatchTable.id

        val hitIndex = pendingWin?.winningBoxId?.let { activeBoxes.indexOf(it) } ?: -1
        activeBoxes.forEachIndexed { index, boxId ->
            val itemResult = when {
                pendingWin == null -> "LOSE"
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

        if (pendingWin != null) {
            val pendingWinId = PendingWinTable.insert {
                it[jackpotId] = pendingWin!!.jackpotId.name
                it[PendingWinTable.tableId] = pendingWin!!.tableId
                it[winningBoxId] = pendingWin!!.winningBoxId
                it[dealerConfirmed] = false
                it[winAmount] = pendingWin!!.winAmount
                it[status] = "WAITING_CONFIRM"
                it[createdAt] = now
                it[updatedAt] = now
            } get PendingWinTable.id

            JackpotHitTable.insert {
                it[jackpotId] = pendingWin!!.jackpotId.name
                it[JackpotHitTable.tableId] = pendingWin!!.tableId
                it[triggerBoxId] = pendingWin!!.winningBoxId
                it[winAmount] = pendingWin!!.winAmount
                it[JackpotHitTable.betBatchId] = betBatchId
                it[hitAt] = now
                it[payoutConfirmedAt] = null
                it[confirmedBoxId] = null
                it[status] = "PENDING_CONFIRM"
            }

            val nextStateVersion = currentStateVersion + 1
            val eventId = SyncEventTable.insert {
                it[createdAt] = now
                it[type] = SyncEventType.JackpotHitDetected.name
                it[payloadJson] =
                    """{"jackpotId":"${pendingWin!!.jackpotId.name}","tableId":${pendingWin!!.tableId},"boxId":${pendingWin!!.winningBoxId},"winAmount":${pendingWin!!.winAmount}}"""
                it[stateVersion] = nextStateVersion
            } get SyncEventTable.eventId

            SystemStateTable.update({ SystemStateTable.id eq 1 }) {
                it[SystemStateTable.systemMode] = SystemMode.PAYOUT_PENDING.name
                it[SystemStateTable.pendingWinId] = pendingWinId
                it[SystemStateTable.stateVersion] = nextStateVersion
                it[SystemStateTable.lastEventId] = eventId
                it[SystemStateTable.updatedAt] = now
            }

            return@dbQuery ConfirmBetsResult(nextStateVersion, eventId)
        } else {
            val nextStateVersion = currentStateVersion + 1
            val eventId = SyncEventTable.insert {
                it[createdAt] = now
                it[type] = SyncEventType.BetsConfirmed.name
                it[payloadJson] =
                    """{"tableId":$tableId,"boxIds":[${activeBoxes.joinToString(",")}]}"""
                it[stateVersion] = nextStateVersion
            } get SyncEventTable.eventId

            SystemStateTable.update({ SystemStateTable.id eq 1 }) {
                it[stateVersion] = nextStateVersion
                it[lastEventId] = eventId
                it[updatedAt] = now
            }

            return@dbQuery ConfirmBetsResult(nextStateVersion, eventId)
        }
    }

    suspend fun confirmPayoutTransactional(
        tableId: Int
    ): ConfirmBetsResult = dbQuery {
        val now = System.currentTimeMillis()

        val systemRow = SystemStateTable.selectAll().single()
        val systemMode = SystemMode.valueOf(systemRow[SystemStateTable.systemMode])
        require(systemMode == SystemMode.PAYOUT_PENDING) { "System is not in PAYOUT_PENDING" }

        val pendingWinId = systemRow[SystemStateTable.pendingWinId]
            ?: error("No pending win")

        val pendingWinRow = PendingWinTable.selectAll()
            .where { PendingWinTable.id eq pendingWinId }
            .singleOrNull()
            ?: error("Pending win row not found")

        val pendingTableId = pendingWinRow[PendingWinTable.tableId]
        require(pendingTableId == tableId) {
            "Pending win belongs to table $pendingTableId"
        }

        val dealerConfirmed = pendingWinRow[PendingWinTable.dealerConfirmed]
        require(dealerConfirmed) { "Dealer has not confirmed winning box" }

        val jackpotId = pendingWinRow[PendingWinTable.jackpotId]
        val winningBoxId = pendingWinRow[PendingWinTable.winningBoxId]

        val jackpotConfigRow = JackpotConfigTable.selectAll()
            .where { JackpotConfigTable.jackpotId eq jackpotId }
            .singleOrNull()
            ?: error("Jackpot config not found for $jackpotId")

        val resetAmount = jackpotConfigRow[JackpotConfigTable.resetAmount]

        JackpotStateTable.update({ JackpotStateTable.jackpotId eq jackpotId }) {
            it[currentAmount] = resetAmount
            it[gamesSinceLastHit] = 0
            it[updatedAt] = now
        }

        val pendingHitRow = JackpotHitTable.selectAll()
            .where {
                (JackpotHitTable.jackpotId eq jackpotId) and
                        (JackpotHitTable.tableId eq tableId) and
                        (JackpotHitTable.status eq "PENDING_CONFIRM")
            }
            .orderBy(JackpotHitTable.id to org.jetbrains.exposed.sql.SortOrder.ASC)
            .lastOrNull()

        if (pendingHitRow != null) {
            JackpotHitTable.update({ JackpotHitTable.id eq pendingHitRow[JackpotHitTable.id] }) {
                it[payoutConfirmedAt] = now
                it[confirmedBoxId] = winningBoxId
                it[status] = "CONFIRMED"
            }
        }

        val nextStateVersion = systemRow[SystemStateTable.stateVersion] + 1

        val eventId = SyncEventTable.insert {
            it[createdAt] = now
            it[type] = SyncEventType.PayoutConfirmed.name
            it[payloadJson] = """{"tableId":$tableId,"boxId":$winningBoxId}"""
            it[stateVersion] = nextStateVersion
        } get SyncEventTable.eventId

        SystemStateTable.update({ SystemStateTable.id eq 1L }) {
            it[SystemStateTable.systemMode] = SystemMode.ACCEPTING_BETS.name
            it[SystemStateTable.pendingWinId] = null
            it[SystemStateTable.stateVersion] = nextStateVersion
            it[SystemStateTable.lastEventId] = eventId
            it[SystemStateTable.updatedAt] = now
        }

        ConfirmBetsResult(
            stateVersion = nextStateVersion,
            lastEventId = eventId
        )
    }

    suspend fun selectPayoutBoxTransactional(
        tableId: Int,
        boxId: Int
    ): ConfirmBetsResult = dbQuery {
        val now = System.currentTimeMillis()

        val systemRow = SystemStateTable.selectAll().single()
        val systemMode = SystemMode.valueOf(systemRow[SystemStateTable.systemMode])
        require(systemMode == SystemMode.PAYOUT_PENDING) { "System is not in PAYOUT_PENDING" }

        val pendingWinId = systemRow[SystemStateTable.pendingWinId]
            ?: error("No pending win")

        val pendingWinRow = PendingWinTable.selectAll()
            .where { PendingWinTable.id eq pendingWinId }
            .singleOrNull()
            ?: error("Pending win row not found")

        val pendingTableId = pendingWinRow[PendingWinTable.tableId]
        require(pendingTableId == tableId) {
            "Pending win belongs to table $pendingTableId"
        }

        val winningBoxId = pendingWinRow[PendingWinTable.winningBoxId]
        if (winningBoxId != boxId) {
            return@dbQuery ConfirmBetsResult(
                stateVersion = systemRow[SystemStateTable.stateVersion],
                lastEventId = systemRow[SystemStateTable.lastEventId]
            )
        }

        val alreadyConfirmed = pendingWinRow[PendingWinTable.dealerConfirmed]
        if (alreadyConfirmed) {
            return@dbQuery ConfirmBetsResult(
                stateVersion = systemRow[SystemStateTable.stateVersion],
                lastEventId = systemRow[SystemStateTable.lastEventId]
            )
        }

        PendingWinTable.update({ PendingWinTable.id eq pendingWinId }) {
            it[dealerConfirmed] = true
            it[updatedAt] = now
        }

        val nextStateVersion = systemRow[SystemStateTable.stateVersion] + 1

        val eventId = SyncEventTable.insert {
            it[createdAt] = now
            it[type] = SyncEventType.PayoutSelectedBox.name
            it[payloadJson] = """{"tableId":$tableId,"boxId":$boxId}"""
            it[stateVersion] = nextStateVersion
        } get SyncEventTable.eventId

        SystemStateTable.update({ SystemStateTable.id eq 1L }) {
            it[stateVersion] = nextStateVersion
            it[lastEventId] = eventId
            it[updatedAt] = now
        }

        ConfirmBetsResult(
            stateVersion = nextStateVersion,
            lastEventId = eventId
        )
    }
}