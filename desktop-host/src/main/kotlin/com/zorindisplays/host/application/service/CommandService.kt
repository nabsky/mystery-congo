package com.zorindisplays.host.application.service

import com.zorindisplays.host.application.command.*
import com.zorindisplays.host.domain.result.CommandResult

interface CommandService {
    suspend fun toggleBox(command: ToggleBoxCommand): CommandResult
    suspend fun confirmBets(command: ConfirmBetsCommand): CommandResult
    suspend fun selectPayoutBox(command: SelectPayoutBoxCommand): CommandResult
    suspend fun confirmPayout(command: ConfirmPayoutCommand): CommandResult
}

