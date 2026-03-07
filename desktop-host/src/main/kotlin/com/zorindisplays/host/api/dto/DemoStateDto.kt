package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DemoStateDto(
    val tables: List<TableDto> = emptyList(),
    val jackpots: Map<String, Long> = emptyMap(),
    val systemMode: SystemModeDto = SystemModeDto.ACCEPTING_BETS,
    val pendingWin: PendingWinDto? = null,
) {
    @Serializable
    data class TableDto(
        val tableId: Int,
        val activeBoxes: Set<Int> = emptySet(),
        val recentBoxes: Set<Int> = emptySet(),
        val isActive: Boolean = true,
    )

    @Serializable
    enum class SystemModeDto {
        ACCEPTING_BETS,
        PAYOUT_PENDING,
    }

    @Serializable
    data class PendingWinDto(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    )

    companion object {
        fun stub(): DemoStateDto {
            return DemoStateDto(
                tables = List(8) { index ->
                    TableDto(
                        tableId = index + 1,
                        activeBoxes = emptySet(),
                        recentBoxes = emptySet(),
                        isActive = false
                    )
                },
                jackpots = mapOf(
                    "RUBY" to 100_000L,
                    "GOLD" to 20_000L,
                    "JADE" to 5_000L
                ),
                systemMode = SystemModeDto.ACCEPTING_BETS,
                pendingWin = null
            )
        }
    }
}