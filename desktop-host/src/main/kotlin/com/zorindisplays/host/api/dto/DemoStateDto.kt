package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DemoStateDto(
    val tables: List<TableDto>,
    val jackpots: List<JackpotDto>,
    val pendingWin: PendingWinDto?,
    val systemMode: SystemModeDto,
    val stateVersion: Long,
    val lastEventId: Long
) {
    companion object {
        fun stub(): DemoStateDto {
            // TODO: Replace with real projection from state
            return DemoStateDto(
                tables = List(8) { TableDto(it + 1, emptyList(), emptyList(), true, null) },
                jackpots = listOf(
                    JackpotDto("RUBY", 0, 0),
                    JackpotDto("GOLD", 0, 0),
                    JackpotDto("JADE", 0, 0)
                ),
                pendingWin = null,
                systemMode = SystemModeDto("ACCEPTING_BETS"),
                stateVersion = 0,
                lastEventId = 0
            )
        }
    }
}

@Serializable
data class TableDto(
    val tableId: Int,
    val activeBoxes: List<Int>,
    val recentBoxes: List<Int>,
    val isActive: Boolean,
    val lastSeenAt: Long?
)

@Serializable
data class PendingWinDto(
    val jackpotId: String,
    val tableId: Int,
    val boxId: Int?,
    val winAmount: Long,
    val createdAt: Long?
)

@Serializable
data class SystemModeDto(val mode: String)

@Serializable
data class JackpotDto(
    val jackpotId: String,
    val currentAmount: Long,
    val gamesSinceLastHit: Int
)

