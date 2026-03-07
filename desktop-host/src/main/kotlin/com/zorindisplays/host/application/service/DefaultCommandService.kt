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
import com.zorindisplays.host.infrastructure.repository.HostWriteRepository

class DefaultCommandService(
    private val config: AppConfig,
    private val stateRepository: ExposedStateRepository,
    private val tableSelectionRepository: ExposedTableSelectionRepository,
    private val syncEventRepository: ExposedSyncEventRepository,
    private val jackpotRepository: ExposedJackpotRepository,
    private val pendingWinRepository: ExposedPendingWinRepository,
    private val hostWriteRepository: HostWriteRepository,
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

        hostWriteRepository.confirmBetsTransactional(
            tableId = command.tableId,
            recentBoxTtlMs = config.recentBoxTtlMs,
            randomRoll = { until -> randomProvider.nextInt(until) }
        )

        return CommandResult.Accepted
    }

    override suspend fun selectPayoutBox(command: SelectPayoutBoxCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        if (state.systemMode != SystemMode.PAYOUT_PENDING) {
            return CommandResult.Rejected("System is not in PAYOUT_PENDING")
        }

        if (command.tableId !in 1..config.tableCount) {
            return CommandResult.Rejected("Invalid tableId=${command.tableId}")
        }

        if (command.boxId !in 1..config.boxCount) {
            return CommandResult.Rejected("Invalid boxId=${command.boxId}")
        }

        hostWriteRepository.selectPayoutBoxTransactional(
            tableId = command.tableId,
            boxId = command.boxId
        )

        return CommandResult.Accepted
    }

    override suspend fun confirmPayout(command: ConfirmPayoutCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

        if (state.systemMode != SystemMode.PAYOUT_PENDING) {
            return CommandResult.Rejected("System is not in PAYOUT_PENDING")
        }

        if (command.tableId !in 1..config.tableCount) {
            return CommandResult.Rejected("Invalid tableId=${command.tableId}")
        }

        return try {
            hostWriteRepository.confirmPayoutTransactional(command.tableId)
            CommandResult.Accepted
        } catch (e: IllegalArgumentException) {
            CommandResult.Rejected(e.message ?: "Invalid payout confirmation")
        } catch (e: IllegalStateException) {
            CommandResult.Rejected(e.message ?: "Invalid payout state")
        } catch (e: Throwable) {
            CommandResult.Failed(e.message ?: "Failed to confirm payout")
        }
    }
}