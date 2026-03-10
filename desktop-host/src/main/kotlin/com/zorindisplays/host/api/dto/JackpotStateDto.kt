package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class JackpotStateDto(
    val tables: List<TableDto> = emptyList(),
    val jackpots: Map<String, JackpotInfoDto> = emptyMap(),
    val jackpotGrowth: Map<String, Long> = emptyMap(),
    val jackpotSettings: Map<String, JackpotSettingsDto> = emptyMap(),
    val systemMode: SystemModeDto = SystemModeDto.ACCEPTING_BETS,
    val pendingWin: PendingWinDto? = null,
    val currencyCode: String,
) {
    @Serializable
    data class TableDto(
        val tableId: Int,
        val activeBoxes: Set<Int> = emptySet(),
        val recentBoxes: Set<Int> = emptySet(),
        val isActive: Boolean = true,
    )

    @Serializable
    data class JackpotInfoDto(
        val currentAmount: Long,
        val gamesSinceLastHit: Int,
    )

    @Serializable
    data class JackpotSettingsDto(
        val resetAmount: Long,
        val contributionPerBet: Long,
        val hitFrequencyGames: Int,
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
        fun stub(): JackpotStateDto {
            return JackpotStateDto(
                tables = List(8) { index ->
                    TableDto(
                        tableId = index + 1,
                        activeBoxes = emptySet(),
                        recentBoxes = emptySet(),
                        isActive = false
                    )
                },
                jackpots = mapOf(
                    "RUBY" to JackpotInfoDto(
                        currentAmount = 100_000L,
                        gamesSinceLastHit = 0
                    ),
                    "GOLD" to JackpotInfoDto(
                        currentAmount = 20_000L,
                        gamesSinceLastHit = 0
                    ),
                    "JADE" to JackpotInfoDto(
                        currentAmount = 5_000L,
                        gamesSinceLastHit = 0
                    )
                ),
                jackpotGrowth = mapOf(
                    "RUBY" to 0L,
                    "GOLD" to 0L,
                    "JADE" to 0L
                ),
                jackpotSettings = mapOf(
                    "RUBY" to JackpotSettingsDto(
                        resetAmount = 100_000L,
                        contributionPerBet = 100L,
                        hitFrequencyGames = 1000
                    ),
                    "GOLD" to JackpotSettingsDto(
                        resetAmount = 20_000L,
                        contributionPerBet = 50L,
                        hitFrequencyGames = 300
                    ),
                    "JADE" to JackpotSettingsDto(
                        resetAmount = 5_000L,
                        contributionPerBet = 10L,
                        hitFrequencyGames = 100
                    )
                ),
                systemMode = SystemModeDto.ACCEPTING_BETS,
                pendingWin = null,
                currencyCode = "USD"
            )
        }
    }
}