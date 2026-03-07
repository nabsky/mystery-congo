package com.zorindisplays.host.domain.model

data class JackpotState(
    val jackpotId: JackpotId,
    val currentAmount: Long,
    val gamesSinceLastHit: Int
)