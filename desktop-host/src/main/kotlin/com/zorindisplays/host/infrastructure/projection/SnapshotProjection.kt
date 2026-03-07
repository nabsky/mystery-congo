package com.zorindisplays.host.infrastructure.projection

import com.zorindisplays.host.api.dto.DemoStateDto
import com.zorindisplays.host.api.dto.JackpotDto
import com.zorindisplays.host.api.dto.PendingWinDto
import com.zorindisplays.host.api.dto.SystemModeDto
import com.zorindisplays.host.api.dto.TableDto
import com.zorindisplays.host.domain.model.HostState

class SnapshotProjection {
    fun project(state: HostState): DemoStateDto {
        return DemoStateDto(
            tables = state.tables.map { table ->
                TableDto(
                    tableId = table.tableId,
                    activeBoxes = table.activeBoxes.sorted(),
                    recentBoxes = table.recentBoxes.sorted(),
                    isActive = table.isActive,
                    lastSeenAt = table.lastSeenAt
                )
            },
            jackpots = state.jackpots.values
                .sortedBy { it.jackpotId.ordinal }
                .map { jackpot ->
                    JackpotDto(
                        jackpotId = jackpot.jackpotId.name,
                        currentAmount = jackpot.currentAmount,
                        gamesSinceLastHit = jackpot.gamesSinceLastHit
                    )
                },
            pendingWin = state.pendingWin?.let { pending ->
                PendingWinDto(
                    jackpotId = pending.jackpotId.name,
                    tableId = pending.tableId,
                    boxId = pending.boxId,
                    winAmount = pending.winAmount,
                    createdAt = pending.createdAt
                )
            },
            systemMode = SystemModeDto(state.systemMode.name),
            stateVersion = state.stateVersion,
            lastEventId = state.lastEventId
        )
    }
}