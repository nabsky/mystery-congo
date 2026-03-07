package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminJackpotSettingsDto(
    val jackpotId: String,
    val resetAmount: Long,
    val contributionPerBet: Long,
    val hitFrequencyGames: Int,
    val priorityOrder: Int,
    val enabled: Boolean
)

@Serializable
data class UpdateAdminJackpotSettingsRequest(
    val resetAmount: Long,
    val contributionPerBet: Long,
    val hitFrequencyGames: Int,
    val priorityOrder: Int,
    val enabled: Boolean
)