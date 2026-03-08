package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.admin.AdminLiveMessage
import com.zorindisplays.host.admin.AdminWsHub
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class HostWriteRepository {

    data class LiveEventToBroadcast(
        val eventId: Long,
        val ts: Long,
        val type: SyncEventType,
        val payloadJson: String,
        val stateVersion: Long
    )

    data class CommandWriteResult(
        val stateVersion: Long,
        val lastEventId: Long,
        val liveEvent: LiveEventToBroadcast? = null
    )

    private fun loadSystemRow(): ResultRow =
        SystemStateTable.selectAll().single()

    private fun currentSystemMode(row: ResultRow): SystemMode =
        SystemMode.valueOf(row[SystemStateTable.systemMode])

    private fun nextStateVersion(row: ResultRow): Long =
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
            if (systemMode != null) {
                it[SystemStateTable.systemMode] = systemMode.name
            }
            if (updatePendingWin) {
                it[SystemStateTable.pendingWinId] = pendingWinId
            }
            it[stateVersion] = nextStateVersion
            it[lastEventId] = eventId
            it[updatedAt] = now
        }
    }

    private suspend fun broadcastLiveEvent(event: LiveEventToBroadcast) {
        AdminWsHub.broadcast(
            Json.encodeToString(
                AdminLiveMessage(
                    type = "event",
                    eventType = event.type.name,
                    payloadJson = event.payloadJson,
                    stateVersion = event.stateVersion,
                    eventId = event.eventId,
                    ts = event.ts
                )
            )
        )
    }

    suspend fun toggleBox(
        tableId: Int,
        boxId: Int
    ): CommandWriteResult {
        val result = dbQuery {
            val now = System.currentTimeMillis()

            val systemRow = loadSystemRow()
            require(currentSystemMode(systemRow) == SystemMode.ACCEPTING_BETS) {
                "System is not accepting bets"
            }

            touchTable(tableId, now)

            val exists = TableActiveBoxTable.selectAll()
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
            val payload = """{"tableId":$tableId,"boxId":$boxId}"""

            val eventId = insertSyncEvent(
                now = now,
                type = SyncEventType.BoxToggled,
                payload = payload,
                stateVersion = nextVersion
            )

            updateSystemState(
                nextStateVersion = nextVersion,
                eventId = eventId,
                now = now
            )

            CommandWriteResult(
                stateVersion = nextVersion,
                lastEventId = eventId,
                liveEvent = LiveEventToBroadcast(
                    eventId = eventId,
                    ts = now,
                    type = SyncEventType.BoxToggled,
                    payloadJson = payload,
                    stateVersion = nextVersion
                )
            )
        }

        result.liveEvent?.let { broadcastLiveEvent(it) }
        return result
    }

    suspend fun confirmBets(
        tableId: Int,
        recentBoxTtlMs: Long,
        randomRoll: (Int) -> Int
    ): CommandWriteResult {
        val result = dbQuery {
            val now = System.currentTimeMillis()

            val systemRow = loadSystemRow()
            require(currentSystemMode(systemRow) == SystemMode.ACCEPTING_BETS) {
                "System is not accepting bets"
            }

            val currentStateVersion = systemRow[SystemStateTable.stateVersion]

            val activeBoxes = TableActiveBoxTable.selectAll()
                .where { TableActiveBoxTable.tableId eq tableId }
                .map { it[TableActiveBoxTable.boxId] }
                .sorted()

            if (activeBoxes.isEmpty()) {
                return@dbQuery CommandWriteResult(
                    stateVersion = currentStateVersion,
                    lastEventId = systemRow[SystemStateTable.lastEventId]
                )
            }

            touchTable(tableId, now)

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

                        jackpotStates[cfg.jackpotId] = state.copy(
                            gamesSinceLastHit = 0
                        )

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
                val payload =
                    """{"jackpotId":"${hit.jackpotId.name}","tableId":${hit.tableId},"boxId":${hit.winningBoxId},"winAmount":${hit.winAmount}}"""

                val eventId = insertSyncEvent(
                    now = now,
                    type = SyncEventType.JackpotHitDetected,
                    payload = payload,
                    stateVersion = nextVersion
                )

                updateSystemState(
                    nextStateVersion = nextVersion,
                    eventId = eventId,
                    now = now,
                    systemMode = SystemMode.PAYOUT_PENDING,
                    pendingWinId = pendingWinId,
                    updatePendingWin = true
                )

                return@dbQuery CommandWriteResult(
                    stateVersion = nextVersion,
                    lastEventId = eventId,
                    liveEvent = LiveEventToBroadcast(
                        eventId = eventId,
                        ts = now,
                        type = SyncEventType.JackpotHitDetected,
                        payloadJson = payload,
                        stateVersion = nextVersion
                    )
                )
            }

            val nextVersion = currentStateVersion + 1
            val payload =
                """{"tableId":$tableId,"boxIds":[${activeBoxes.joinToString(",")}]}"""

            val eventId = insertSyncEvent(
                now = now,
                type = SyncEventType.BetsConfirmed,
                payload = payload,
                stateVersion = nextVersion
            )

            updateSystemState(
                nextStateVersion = nextVersion,
                eventId = eventId,
                now = now
            )

            CommandWriteResult(
                stateVersion = nextVersion,
                lastEventId = eventId,
                liveEvent = LiveEventToBroadcast(
                    eventId = eventId,
                    ts = now,
                    type = SyncEventType.BetsConfirmed,
                    payloadJson = payload,
                    stateVersion = nextVersion
                )
            )
        }

        result.liveEvent?.let { broadcastLiveEvent(it) }
        return result
    }

    suspend fun selectPayoutBox(
        tableId: Int,
        boxId: Int
    ): CommandWriteResult {
        val result = dbQuery {
            val now = System.currentTimeMillis()

            val systemRow = loadSystemRow()
            require(currentSystemMode(systemRow) == SystemMode.PAYOUT_PENDING) {
                "System is not in PAYOUT_PENDING"
            }

            val pendingWinId = systemRow[SystemStateTable.pendingWinId]
                ?: error("No pending win")

            val row = PendingWinTable.selectAll()
                .where { PendingWinTable.id eq pendingWinId }
                .single()

            require(row[PendingWinTable.tableId] == tableId) {
                "Pending win belongs to table ${row[PendingWinTable.tableId]}"
            }

            val winningBox = row[PendingWinTable.winningBoxId]

            if (winningBox != boxId) {
                return@dbQuery CommandWriteResult(
                    stateVersion = systemRow[SystemStateTable.stateVersion],
                    lastEventId = systemRow[SystemStateTable.lastEventId]
                )
            }

            if (row[PendingWinTable.dealerConfirmed]) {
                return@dbQuery CommandWriteResult(
                    stateVersion = systemRow[SystemStateTable.stateVersion],
                    lastEventId = systemRow[SystemStateTable.lastEventId]
                )
            }

            PendingWinTable.update({ PendingWinTable.id eq pendingWinId }) {
                it[dealerConfirmed] = true
                it[updatedAt] = now
            }

            val nextVersion = nextStateVersion(systemRow)
            val payload = """{"tableId":$tableId,"boxId":$boxId}"""

            val eventId = insertSyncEvent(
                now = now,
                type = SyncEventType.PayoutSelectedBox,
                payload = payload,
                stateVersion = nextVersion
            )

            updateSystemState(
                nextStateVersion = nextVersion,
                eventId = eventId,
                now = now
            )

            CommandWriteResult(
                stateVersion = nextVersion,
                lastEventId = eventId,
                liveEvent = LiveEventToBroadcast(
                    eventId = eventId,
                    ts = now,
                    type = SyncEventType.PayoutSelectedBox,
                    payloadJson = payload,
                    stateVersion = nextVersion
                )
            )
        }

        result.liveEvent?.let { broadcastLiveEvent(it) }
        return result
    }

    suspend fun confirmPayout(
        tableId: Int
    ): CommandWriteResult {
        val result = dbQuery {
            val now = System.currentTimeMillis()

            val systemRow = loadSystemRow()
            require(currentSystemMode(systemRow) == SystemMode.PAYOUT_PENDING) {
                "System is not in PAYOUT_PENDING"
            }

            val pendingWinId = systemRow[SystemStateTable.pendingWinId]
                ?: error("No pending win")

            val row = PendingWinTable.selectAll()
                .where { PendingWinTable.id eq pendingWinId }
                .single()

            require(row[PendingWinTable.tableId] == tableId) {
                "Pending win belongs to table ${row[PendingWinTable.tableId]}"
            }

            require(row[PendingWinTable.dealerConfirmed]) {
                "Dealer has not confirmed winning box"
            }

            val jackpotId = row[PendingWinTable.jackpotId]
            val winningBoxId = row[PendingWinTable.winningBoxId]

            PendingWinTable.update({ PendingWinTable.id eq pendingWinId }) {
                it[status] = "CONFIRMED"
                it[updatedAt] = now
            }

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
            val payload = """{"tableId":$tableId,"boxId":$winningBoxId}"""

            val eventId = insertSyncEvent(
                now = now,
                type = SyncEventType.PayoutConfirmed,
                payload = payload,
                stateVersion = nextVersion
            )

            updateSystemState(
                nextStateVersion = nextVersion,
                eventId = eventId,
                now = now,
                systemMode = SystemMode.ACCEPTING_BETS,
                pendingWinId = null,
                updatePendingWin = true
            )

            CommandWriteResult(
                stateVersion = nextVersion,
                lastEventId = eventId,
                liveEvent = LiveEventToBroadcast(
                    eventId = eventId,
                    ts = now,
                    type = SyncEventType.PayoutConfirmed,
                    payloadJson = payload,
                    stateVersion = nextVersion
                )
            )
        }

        result.liveEvent?.let { broadcastLiveEvent(it) }
        return result
    }
}