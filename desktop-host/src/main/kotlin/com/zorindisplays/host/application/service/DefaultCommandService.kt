package com.zorindisplays.host.application.service

import com.zorindisplays.host.application.command.ConfirmBetsCommand
import com.zorindisplays.host.application.command.ConfirmPayoutCommand
import com.zorindisplays.host.application.command.SelectPayoutBoxCommand
import com.zorindisplays.host.application.command.ToggleBoxCommand
import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.domain.model.SystemMode
import com.zorindisplays.host.domain.result.CommandResult
import com.zorindisplays.host.infrastructure.repository.ExposedStateRepository
import com.zorindisplays.host.infrastructure.repository.ExposedSyncEventRepository
import com.zorindisplays.host.infrastructure.repository.ExposedTableSelectionRepository

class DefaultCommandService(
    private val stateRepository: ExposedStateRepository,
    private val tableSelectionRepository: ExposedTableSelectionRepository,
    private val syncEventRepository: ExposedSyncEventRepository
) : CommandService {

    override suspend fun toggleBox(command: ToggleBoxCommand): CommandResult {
        val state = stateRepository.getCurrentState()
            ?: return CommandResult.Failed("State not initialized")

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

        stateRepository.updateStateVersionAndLastEvent(
            stateVersion = nextStateVersion,
            lastEventId = eventId
        )

        return CommandResult.Accepted
    }

    override suspend fun confirmBets(command: ConfirmBetsCommand): CommandResult {
        return CommandResult.Rejected("Not implemented yet")
    }

    override suspend fun selectPayoutBox(command: SelectPayoutBoxCommand): CommandResult {
        return CommandResult.Rejected("Not implemented yet")
    }

    override suspend fun confirmPayout(command: ConfirmPayoutCommand): CommandResult {
        return CommandResult.Rejected("Not implemented yet")
    }
}