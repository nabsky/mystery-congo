package com.zorindisplays.display.model

sealed class DemoEvent {
    data class JackpotHit(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    ) : DemoEvent()

    data class DealerPayoutBoxSelected(
        val tableId: Int,
        val boxId: Int,
    ) : DemoEvent()

    data class DealerPayoutConfirmed(
        val tableId: Int,
        val boxId: Int,
    ) : DemoEvent()

    data class BetsConfirmed(
        val tableId: Int,
        val boxIds: Set<Int>
    ) : DemoEvent()

    data object PendingWinReset : DemoEvent()
}