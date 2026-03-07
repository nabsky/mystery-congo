package com.zorindisplays.host.domain.model

data class JackpotConfig(
    val jackpotId: JackpotId,
    val resetAmount: Long,
    val contributionPerBet: Long,
    val hitFrequencyGames: Int,
    val priorityOrder: Int,
    val enabled: Boolean = true
)