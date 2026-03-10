package com.zorindisplays.host.infrastructure.projection

import com.zorindisplays.host.api.dto.JackpotStateDto
import com.zorindisplays.host.domain.model.HostState
import com.zorindisplays.host.domain.model.SystemMode

class SnapshotProjection {
    fun project(state: HostState): JackpotStateDto {
        return JackpotStateDto(
            tables = state.tables
                .sortedBy { it.tableId }
                .map { table ->
                    JackpotStateDto.TableDto(
                        tableId = table.tableId,
                        activeBoxes = table.activeBoxes,
                        recentBoxes = table.recentBoxes,
                        isActive = table.isActive
                    )
                },
            jackpots = state.jackpots
                .mapKeys { (jackpotId, _) -> jackpotId.name }
                .mapValues { (_, jackpotState) ->
                    JackpotStateDto.JackpotInfoDto(
                        currentAmount = jackpotState.currentAmount,
                        gamesSinceLastHit = jackpotState.gamesSinceLastHit
                    )
                },
            jackpotGrowth = state.jackpotGrowth
                .mapKeys { (jackpotId, _) -> jackpotId.name },
            jackpotSettings = state.jackpotSettings
                .mapKeys { (jackpotId, _) -> jackpotId.name }
                .mapValues { (_, settings) ->
                    JackpotStateDto.JackpotSettingsDto(
                        resetAmount = settings.resetAmount,
                        contributionPerBet = settings.contributionPerBet,
                        hitFrequencyGames = settings.hitFrequencyGames
                    )
                },
            systemMode = when (state.systemMode) {
                SystemMode.ACCEPTING_BETS -> JackpotStateDto.SystemModeDto.ACCEPTING_BETS
                SystemMode.PAYOUT_PENDING -> JackpotStateDto.SystemModeDto.PAYOUT_PENDING
            },
            pendingWin = state.pendingWin?.let { pending ->
                JackpotStateDto.PendingWinDto(
                    jackpotId = pending.jackpotId.name,
                    tableId = pending.tableId,
                    boxId = pending.winningBoxId,
                    winAmount = pending.winAmount
                )
            },
            currencyCode = state.currencyCode
        )
    }
}