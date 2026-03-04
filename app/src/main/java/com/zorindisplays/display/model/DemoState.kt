package com.zorindisplays.display.model

data class DemoState(
    val tables: List<Table> = emptyList(),
    val jackpots: Map<String, Long> = emptyMap(), // "RUBY", "GOLD", "JADE"
    val systemMode: SystemMode = SystemMode.ACCEPTING_BETS,
    val pendingWin: PendingWin? = null,
) {
    data class Table(
        val tableId: Int,
        val activeBoxes: Set<Int> = emptySet(),
        val recentBoxes: Set<Int> = emptySet(),
        val isActive: Boolean = true,
    )

    enum class SystemMode {
        ACCEPTING_BETS,
        PAYOUT_PENDING,
    }

    data class PendingWin(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    )
}