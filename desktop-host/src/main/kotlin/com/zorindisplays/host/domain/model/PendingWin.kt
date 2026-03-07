package com.zorindisplays.host.domain.model

data class PendingWin(
    val id: Long? = null,
    val jackpotId: JackpotId,
    val tableId: Int,
    val winningBoxId: Int,
    val dealerConfirmed: Boolean,
    val winAmount: Long,
    val createdAt: Long?
)