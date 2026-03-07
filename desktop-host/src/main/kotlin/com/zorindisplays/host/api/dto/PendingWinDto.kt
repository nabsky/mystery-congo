package com.zorindisplays.host.api.dto

data class PendingWinDto(
    val jackpotId: String,
    val tableId: Int,
    val boxId: Int,
    val winAmount: Long,
)