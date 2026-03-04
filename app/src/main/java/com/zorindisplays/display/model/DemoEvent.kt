package com.zorindisplays.display.model

sealed class DemoEvent {
    data class JackpotHit(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long,
    ) : DemoEvent()
}