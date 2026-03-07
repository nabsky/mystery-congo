package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.domain.model.*
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class HostWriteRepository {

    data class CommandWriteResult(
        val stateVersion: Long,
        val lastEventId: Long
    )

    // ============================================================
    // Helpers
    // ============================================================

    private fun loadSystemRow() =
        SystemStateTable.selectAll().single()

    private fun currentSystemMode(row: ResultRow) =
        SystemMode.valueOf(row[SystemStateTable.systemMode])

    private fun nextStateVersion(row: ResultRow) =
        row[SystemStateTable.stateVersion] + 1

    private fun touchTable(tableId: Int, now: Long) {
        TableStateTable.update({ TableStateTable.tableId eq tableId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }
    }

    private fun insertSyncEvent(
        now: Long,
        type: SyncEventType,
        payload: String,
        stateVersion: Long
    ): Long {
        return SyncEventTable.insert {
            it[createdAt] = now
            it[SyncEventTable.type] = type.name
            it[payloadJson] = payload
            it[SyncEventTable.stateVersion] = stateVersion
        } get SyncEventTable.eventId
    }

    private fun updateSystemState(
        nextStateVersion: Long,
        eventId: Long,
        now: Long,
        systemMode: SystemMode? = null,
        pendingWinId: Long? = null,
        updatePendingWin: Boolean = false
    ) {
        SystemStateTable.update({ SystemStateTable.id eq 1L }) {

            if (systemMode != null)
                it[SystemStateTable.systemMode] = systemMode.name

            if (updatePendingWin)
                it[SystemStateTable.pendingWinId] = pendingWinId

            it[stateVersion] = nextStateVersion
            it[lastEventId] = eventId
            it[updatedAt] = now
        }
    }

    // ============================================================
    // Toggle Box
    // ============================================================

    suspend fun toggleBoxTransactional(
        tableId: Int,
        boxId: Int
    ): CommandWriteResult = dbQuery {

        val now = System.currentTimeMillis()

        val systemRow = loadSystemRow()
        require(currentSystemMode(systemRow) == SystemMode.ACCEPTING_BETS)

        touchTable(tableId, now)

        val exists = TableActiveBoxTable
            .selectAll()
            .where {
                (TableActiveBoxTable.tableId eq tableId) and
                        (TableActiveBoxTable.boxId eq boxId)
            }
            .any()

        if (exists) {
            TableActiveBoxTable.deleteWhere {
                (TableActiveBoxTable.tableId eq tableId) and
                        (TableActiveBoxTable.boxId eq boxId)
            }
        } else {
            TableActiveBoxTable.insert {
                it[TableActiveBoxTable.tableId] = tableId
                it[TableActiveBoxTable.boxId] = boxId
                it[selectedAt] = now
            }
        }

        val nextVersion = nextStateVersion(systemRow)

        val eventId = insertSyncEvent(
            now,
            SyncEventType.BoxToggled,
            """{"tableId":$tableId,"boxId":$boxId}""",
            nextVersion
        )

        updateSystemState(nextVersion, eventId, now)

        CommandWriteResult(nextVersion, eventId)
    }

    // ============================================================
    // Confirm Bets
    // ============================================================

    suspend fun confirmBetsTransactional(
        tableId: Int,
        recentBoxTtlMs: Long,
        randomRoll: (Int) -> Int
    ): CommandWriteResult = dbQuery {

        val now = System.currentTimeMillis()

        val systemRow = loadSystemRow()
        require(currentSystemMode(systemRow) == SystemMode.ACCEPTING_BETS)

        val currentStateVersion = systemRow[SystemStateTable.stateVersion]

        val activeBoxes = TableActiveBoxTable
            .selectAll()
            .where { TableActiveBoxTable.tableId eq tableId }
            .map { it[TableActiveBoxTable.boxId] }
            .sorted()

        if (activeBoxes.isEmpty()) {
            return@dbQuery CommandWriteResult(
                currentStateVersion,
                systemRow[SystemStateTable.lastEventId]
            )
        }

        touchTable(tableId, now)

        // -----------------------------
        // Load jackpot configs
        // -----------------------------

        val jackpotConfigs = JackpotConfigTable.selectAll()
            .map {
                JackpotConfig(
                    jackpotId = JackpotId.valueOf(it[JackpotConfigTable.jackpotId]),
                    resetAmount = it[JackpotConfigTable.resetAmount],
                    contributionPerBet = it[JackpotConfigTable.contributionPerBet],
                    hitFrequencyGames = it[JackpotConfigTable.hitFrequencyGames],
                    priorityOrder = it[JackpotConfigTable.priorityOrder],
                    enabled = it[JackpotConfigTable.enabled]
                )
            }
            .filter { it.enabled }
            .sortedBy { it.priorityOrder }

        val jackpotStates = JackpotStateTable.selectAll()
            .associate {
                val id = JackpotId.valueOf(it[JackpotStateTable.jackpotId])
                id to JackpotState(
                    id,
                    it[JackpotStateTable.currentAmount],
                    it[JackpotStateTable.gamesSinceLastHit]
                )
            }
            .toMutableMap()

        var pendingWin: PendingWin? = null

        activeLoop@ for (boxId in activeBoxes) {

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
                    val state = jackpotStates.getValue(cfg.jackpotId)

                    jackpotStates[cfg.jackpotId] =
                        state.copy(gamesSinceLastHit = 0)

                    pendingWin = PendingWin(
                        jackpotId = cfg.jackpotId,
                        tableId = tableId,
                        winningBoxId = boxId,
                        dealerConfirmed = false,
                        winAmount = state.currentAmount,
                        createdAt = now
                    )

                    break@activeLoop
                }
            }
        }

        jackpotStates.values.forEach { state ->
            JackpotStateTable.update({
                JackpotStateTable.jackpotId eq state.jackpotId.name
            }) {
                it[currentAmount] = state.currentAmount
                it[gamesSinceLastHit] = state.gamesSinceLastHit
                it[updatedAt] = now
            }
        }

        activeBoxes.forEach { boxId ->

            TableRecentBoxTable.deleteWhere {
                (TableRecentBoxTable.tableId eq tableId) and
                        (TableRecentBoxTable.boxId eq boxId)
            }

            TableRecentBoxTable.insert {
                it[TableRecentBoxTable.tableId] = tableId
                it[TableRecentBoxTable.boxId] = boxId
                it[confirmedAt] = now
                it[expiresAt] = now + recentBoxTtlMs
            }
        }

        TableActiveBoxTable.deleteWhere {
            TableActiveBoxTable.tableId eq tableId
        }

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

            val result = when {
                pendingWin == null -> "LOSE"
                index < hitIndex -> "LOSE"
                index == hitIndex -> "WIN"
                else -> "SKIPPED_AFTER_HIT"
            }

            BetBatchItemTable.insert {
                it[BetBatchItemTable.betBatchId] = betBatchId
                it[BetBatchItemTable.boxId] = boxId
                it[seqNo] = index
                it[BetBatchItemTable.result] = result
            }
        }

        val hit = pendingWin

        if (hit != null) {

            val pendingWinId = PendingWinTable.insert {
                it[jackpotId] = hit.jackpotId.name
                it[PendingWinTable.tableId] = hit.tableId
                it[winningBoxId] = hit.winningBoxId
                it[dealerConfirmed] = false
                it[winAmount] = hit.winAmount
                it[status] = "WAITING_CONFIRM"
                it[createdAt] = now
                it[updatedAt] = now
            } get PendingWinTable.id

            JackpotHitTable.insert {
                it[jackpotId] = hit.jackpotId.name
                it[JackpotHitTable.tableId] = hit.tableId
                it[triggerBoxId] = hit.winningBoxId
                it[winAmount] = hit.winAmount
                it[JackpotHitTable.betBatchId] = betBatchId
                it[hitAt] = now
                it[payoutConfirmedAt] = null
                it[confirmedBoxId] = null
                it[status] = "PENDING_CONFIRM"
            }

            val nextVersion = currentStateVersion + 1

            val eventId = insertSyncEvent(
                now,
                SyncEventType.JackpotHitDetected,
                """{"jackpotId":"${hit.jackpotId.name}","tableId":${hit.tableId},"boxId":${hit.winningBoxId},"winAmount":${hit.winAmount}}""",
                nextVersion
            )

            updateSystemState(
                nextVersion,
                eventId,
                now,
                SystemMode.PAYOUT_PENDING,
                pendingWinId,
                true
            )

            return@dbQuery CommandWriteResult(nextVersion, eventId)
        }

        val nextVersion = currentStateVersion + 1

        val eventId = insertSyncEvent(
            now,
            SyncEventType.BetsConfirmed,
            """{"tableId":$tableId,"boxIds":[${activeBoxes.joinToString(",")}]}""",
            nextVersion
        )

        updateSystemState(nextVersion, eventId, now)

        CommandWriteResult(nextVersion, eventId)
    }

    // ============================================================
    // Dealer selects box
    // ============================================================

    suspend fun selectPayoutBoxTransactional(
        tableId: Int,
        boxId: Int
    ): CommandWriteResult = dbQuery {

        val now = System.currentTimeMillis()

        val systemRow = loadSystemRow()
        require(currentSystemMode(systemRow) == SystemMode.PAYOUT_PENDING)

        val pendingWinId = systemRow[SystemStateTable.pendingWinId]
            ?: error("No pending win")

        val row = PendingWinTable.selectAll()
            .where { PendingWinTable.id eq pendingWinId }
            .single()

        require(row[PendingWinTable.tableId] == tableId)

        val winningBox = row[PendingWinTable.winningBoxId]

        if (winningBox != boxId)
            return@dbQuery CommandWriteResult(
                systemRow[SystemStateTable.stateVersion],
                systemRow[SystemStateTable.lastEventId]
            )

        if (row[PendingWinTable.dealerConfirmed])
            return@dbQuery CommandWriteResult(
                systemRow[SystemStateTable.stateVersion],
                systemRow[SystemStateTable.lastEventId]
            )

        PendingWinTable.update({ PendingWinTable.id eq pendingWinId }) {
            it[dealerConfirmed] = true
            it[updatedAt] = now
        }

        val nextVersion = nextStateVersion(systemRow)

        val eventId = insertSyncEvent(
            now,
            SyncEventType.PayoutSelectedBox,
            """{"tableId":$tableId,"boxId":$boxId}""",
            nextVersion
        )

        updateSystemState(nextVersion, eventId, now)

        CommandWriteResult(nextVersion, eventId)
    }

    // ============================================================
    // Confirm payout
    // ============================================================

    suspend fun confirmPayoutTransactional(
        tableId: Int
    ): CommandWriteResult = dbQuery {

        val now = System.currentTimeMillis()

        val systemRow = loadSystemRow()
        require(currentSystemMode(systemRow) == SystemMode.PAYOUT_PENDING)

        val pendingWinId = systemRow[SystemStateTable.pendingWinId]
            ?: error("No pending win")

        val row = PendingWinTable.selectAll()
            .where { PendingWinTable.id eq pendingWinId }
            .single()

        require(row[PendingWinTable.tableId] == tableId)
        require(row[PendingWinTable.dealerConfirmed])

        val jackpotId = row[PendingWinTable.jackpotId]
        val winningBoxId = row[PendingWinTable.winningBoxId]

        val cfg = JackpotConfigTable.selectAll()
            .where { JackpotConfigTable.jackpotId eq jackpotId }
            .single()

        val resetAmount = cfg[JackpotConfigTable.resetAmount]

        JackpotStateTable.update({
            JackpotStateTable.jackpotId eq jackpotId
        }) {
            it[currentAmount] = resetAmount
            it[gamesSinceLastHit] = 0
            it[updatedAt] = now
        }

        JackpotHitTable.update({
            (JackpotHitTable.jackpotId eq jackpotId) and
                    (JackpotHitTable.tableId eq tableId) and
                    (JackpotHitTable.status eq "PENDING_CONFIRM")
        }) {
            it[payoutConfirmedAt] = now
            it[confirmedBoxId] = winningBoxId
            it[status] = "CONFIRMED"
        }

        val nextVersion = nextStateVersion(systemRow)

        val eventId = insertSyncEvent(
            now,
            SyncEventType.PayoutConfirmed,
            """{"tableId":$tableId,"boxId":$winningBoxId}""",
            nextVersion
        )

        updateSystemState(
            nextVersion,
            eventId,
            now,
            SystemMode.ACCEPTING_BETS,
            null,
            true
        )

        CommandWriteResult(nextVersion, eventId)
    }
}