package com.zorindisplays.host.infrastructure.projection

import com.zorindisplays.host.api.dto.DemoStateDto
import com.zorindisplays.host.domain.model.HostState
import com.zorindisplays.host.domain.model.SystemMode

class SnapshotProjection {
    fun project(state: HostState): DemoStateDto {
        return DemoStateDto(
            tables = state.tables
                .sortedBy { it.tableId }
                .map { table ->
                    DemoStateDto.TableDto(
                        tableId = table.tableId,
                        activeBoxes = table.activeBoxes,
                        recentBoxes = table.recentBoxes,
                        isActive = table.isActive
                    )
                },
            jackpots = state.jackpots
                .mapKeys { (jackpotId, _) -> jackpotId.name }
                .mapValues { (_, jackpotState) -> jackpotState.currentAmount },
            systemMode = when (state.systemMode) {
                SystemMode.ACCEPTING_BETS -> DemoStateDto.SystemModeDto.ACCEPTING_BETS
                SystemMode.PAYOUT_PENDING -> DemoStateDto.SystemModeDto.PAYOUT_PENDING
            },
            pendingWin = state.pendingWin?.let { pending ->
                DemoStateDto.PendingWinDto(
                    jackpotId = pending.jackpotId.name,
                    tableId = pending.tableId,
                    boxId = pending.winningBoxId,
                    winAmount = pending.winAmount
                )
            }
        )
    }
}