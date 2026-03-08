package com.zorindisplays.mystery.model

sealed class JackpotEvent {
    data class JackpotHit(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    ) : JackpotEvent()

    data class DealerPayoutBoxSelected(
        val tableId: Int,
        val boxId: Int,
    ) : JackpotEvent()

    data class DealerPayoutConfirmed(
        val tableId: Int,
        val boxId: Int,
    ) : JackpotEvent()

    data class BetsConfirmed(
        val tableId: Int,
        val boxIds: Set<Int>
    ) : JackpotEvent()

    data object PendingWinReset : JackpotEvent()
}