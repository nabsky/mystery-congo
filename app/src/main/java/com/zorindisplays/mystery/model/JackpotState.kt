package com.zorindisplays.mystery.model

import kotlinx.serialization.Serializable


@Serializable
data class JackpotState(
    val tables: List<Table> = emptyList(),
    val jackpots: Map<String, Long> = emptyMap(), // "RUBY", "GOLD", "JADE"
    val systemMode: SystemMode = SystemMode.ACCEPTING_BETS,
    val pendingWin: PendingWin? = null,
) {
    @Serializable
    data class Table(
        val tableId: Int,
        val activeBoxes: Set<Int> = emptySet(),
        val recentBoxes: Set<Int> = emptySet(),
        val isActive: Boolean = true,
    )

    @Serializable
    enum class SystemMode {
        ACCEPTING_BETS,
        PAYOUT_PENDING,
    }

    @Serializable
    data class PendingWin(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    )
}