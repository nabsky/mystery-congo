package com.zorindisplays.host.application.service

import com.zorindisplays.host.app.AppConfig
import com.zorindisplays.host.application.command.ConfirmBetsCommand
import com.zorindisplays.host.application.command.ConfirmPayoutCommand
import com.zorindisplays.host.application.command.SelectPayoutBoxCommand
import com.zorindisplays.host.application.command.ToggleBoxCommand
import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.JackpotState
import com.zorindisplays.host.domain.model.PendingWin
import com.zorindisplays.host.domain.model.SystemMode
import com.zorindisplays.host.domain.result.CommandResult
import com.zorindisplays.host.infrastructure.repository.ExposedJackpotRepository
import com.zorindisplays.host.infrastructure.repository.ExposedPendingWinRepository
import com.zorindisplays.host.infrastructure.repository.ExposedStateRepository
import com.zorindisplays.host.infrastructure.repository.ExposedSyncEventRepository
import com.zorindisplays.host.infrastructure.repository.ExposedTableSelectionRepository

class DefaultCommandService(
    private val config: AppConfig,
    private val stateRepository: ExposedStateRepository,
    private val tableSelectionRepository: ExposedTableSelectionRepository,
    private val syncEventRepository: ExposedSyncEventRepository,
    private val jackpotRepository: ExposedJackpotRepository,
    private val pendingWinRepository: ExposedPendingWinRepository,
    private val randomProvider: RandomProvider = DefaultRandomProvider
) : CommandService {

    override suspend fun toggleBox(command: ToggleBoxCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        if (command.tableId !in 1..config.tableCount) {
            return CommandResult.Rejected("Invalid tableId=${command.tableId}")
        }

        if (command.boxId !in 1..config.boxCount) {
            return CommandResult.Rejected("Invalid boxId=${command.boxId}")
        }

        if (state.systemMode != SystemMode.ACCEPTING_BETS) {
            return CommandResult.Rejected("System is not accepting bets")
        }

        tableSelectionRepository.touchTable(command.tableId, System.currentTimeMillis())
        tableSelectionRepository.toggleBox(command.tableId, command.boxId)

        val nextStateVersion = state.stateVersion + 1
        val eventId = syncEventRepository.append(
            type = SyncEventType.BoxToggled,
            payloadJson = """{"tableId":${command.tableId},"boxId":${command.boxId}}""",
            stateVersion = nextStateVersion
        )

        stateRepository.updateStateVersionAndLastEvent(nextStateVersion, eventId)
        return CommandResult.Accepted
    }

    override suspend fun confirmBets(command: ConfirmBetsCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        if (command.tableId !in 1..config.tableCount) {
            return CommandResult.Rejected("Invalid tableId=${command.tableId}")
        }

        if (state.systemMode != SystemMode.ACCEPTING_BETS) {
            return CommandResult.Rejected("System is not accepting bets")
        }

        val activeBoxes = tableSelectionRepository.getActiveBoxes(command.tableId).sorted()
        if (activeBoxes.isEmpty()) {
            return CommandResult.Accepted
        }

        val jackpotConfigs = jackpotRepository.getConfigs()
            .filter { it.enabled }
            .sortedBy { it.priorityOrder }

        val jackpotStatesById = jackpotRepository.getStates()
            .associateBy { it.jackpotId }
            .toMutableMap()

        var hitPendingWin: PendingWin? = null

        activeBoxesLoop@ for (boxId in activeBoxes) {
            jackpotConfigs.forEach { jackpotConfig ->
                val current = jackpotStatesById.getValue(jackpotConfig.jackpotId)
                jackpotStatesById[jackpotConfig.jackpotId] = current.copy(
                    currentAmount = current.currentAmount + jackpotConfig.contributionPerBet,
                    gamesSinceLastHit = current.gamesSinceLastHit + 1
                )
            }

            for (jackpotConfig in jackpotConfigs) {
                val roll = randomProvider.nextInt(jackpotConfig.hitFrequencyGames)
                if (roll == 0) {
                    val updatedState = jackpotStatesById.getValue(jackpotConfig.jackpotId)
                    jackpotStatesById[jackpotConfig.jackpotId] = updatedState.copy(
                        gamesSinceLastHit = 0
                    )

                    hitPendingWin = PendingWin(
                        jackpotId = jackpotConfig.jackpotId,
                        tableId = command.tableId,
                        winningBoxId = boxId,
                        dealerConfirmed = false,
                        winAmount = updatedState.currentAmount,
                        createdAt = System.currentTimeMillis()
                    )
                    break@activeBoxesLoop
                }
            }
        }

        val now = System.currentTimeMillis()
        tableSelectionRepository.touchTable(command.tableId, now)
        tableSelectionRepository.markRecentBoxes(
            tableId = command.tableId,
            boxIds = activeBoxes.toSet(),
            ttlMs = config.recentBoxTtlMs
        )
        tableSelectionRepository.clearActiveBoxes(command.tableId)
        jackpotRepository.saveStates(jackpotStatesById.values.toList())

        val nextStateVersion = state.stateVersion + 1

        if (hitPendingWin != null) {
            val savedPendingWinId = pendingWinRepository.savePendingWin(hitPendingWin)
            stateRepository.updateSystemMode(SystemMode.PAYOUT_PENDING)

            val eventId = syncEventRepository.append(
                type = SyncEventType.JackpotHitDetected,
                payloadJson = """
                    {"jackpotId":"${hitPendingWin.jackpotId.name}","tableId":${hitPendingWin.tableId},"boxId":${hitPendingWin.winningBoxId},"winAmount":${hitPendingWin.winAmount}}
                """.trimIndent().replace("\n", "").replace(" ", ""),
                stateVersion = nextStateVersion
            )

            stateRepository.updateStateVersionAndLastEvent(nextStateVersion, eventId)
            return CommandResult.Accepted
        }

        val boxIdsJson = activeBoxes.joinToString(",")
        val eventId = syncEventRepository.append(
            type = SyncEventType.BetsConfirmed,
            payloadJson = """{"tableId":${command.tableId},"boxIds":[$boxIdsJson]}""",
            stateVersion = nextStateVersion
        )

        stateRepository.updateStateVersionAndLastEvent(nextStateVersion, eventId)
        return CommandResult.Accepted
    }

    override suspend fun selectPayoutBox(command: SelectPayoutBoxCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        val pendingWin = state.pendingWin
            ?: return CommandResult.Rejected("No pending win")

        if (state.systemMode != SystemMode.PAYOUT_PENDING) {
            return CommandResult.Rejected("System is not in PAYOUT_PENDING")
        }

        if (pendingWin.tableId != command.tableId) {
            return CommandResult.Rejected("Pending win belongs to table ${pendingWin.tableId}")
        }

        if (pendingWin.winningBoxId != command.boxId) {
            return CommandResult.Accepted
        }

        if (!pendingWin.dealerConfirmed && pendingWin.id != null) {
            pendingWinRepository.markDealerConfirmed(pendingWin.id)

            val nextStateVersion = state.stateVersion + 1
            val eventId = syncEventRepository.append(
                type = SyncEventType.PayoutSelectedBox,
                payloadJson = """{"tableId":${command.tableId},"boxId":${command.boxId}}""",
                stateVersion = nextStateVersion
            )

            stateRepository.updateStateVersionAndLastEvent(nextStateVersion, eventId)
        }

        return CommandResult.Accepted
    }

    override suspend fun confirmPayout(command: ConfirmPayoutCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        val pendingWin = state.pendingWin
            ?: return CommandResult.Rejected("No pending win")

        if (state.systemMode != SystemMode.PAYOUT_PENDING) {
            return CommandResult.Rejected("System is not in PAYOUT_PENDING")
        }

        if (pendingWin.tableId != command.tableId) {
            return CommandResult.Rejected("Pending win belongs to table ${pendingWin.tableId}")
        }

        if (!pendingWin.dealerConfirmed) {
            return CommandResult.Rejected("Dealer has not confirmed winning box")
        }

        val jackpotConfigs = jackpotRepository.getConfigs()
        val jackpotStates = jackpotRepository.getStates().associateBy { it.jackpotId }.toMutableMap()

        val winningConfig = jackpotConfigs.firstOrNull { it.jackpotId == pendingWin.jackpotId }
            ?: return CommandResult.Failed("Jackpot config not found for ${pendingWin.jackpotId}")

        val winningState = jackpotStates[pendingWin.jackpotId]
            ?: return CommandResult.Failed("Jackpot state not found for ${pendingWin.jackpotId}")

        jackpotStates[pendingWin.jackpotId] = winningState.copy(
            currentAmount = winningConfig.resetAmount,
            gamesSinceLastHit = 0
        )

        jackpotRepository.saveStates(jackpotStates.values.toList())
        pendingWinRepository.clearPendingWin()
        stateRepository.updateSystemMode(SystemMode.ACCEPTING_BETS)

        val nextStateVersion = state.stateVersion + 1
        val eventId = syncEventRepository.append(
            type = SyncEventType.PayoutConfirmed,
            payloadJson = """{"tableId":${command.tableId},"boxId":${pendingWin.winningBoxId}}""",
            stateVersion = nextStateVersion
        )

        stateRepository.updateStateVersionAndLastEvent(nextStateVersion, eventId)
        return CommandResult.Accepted
    }
}