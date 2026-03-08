package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminServerSettingsDto(
    val currencyCode: String,
    val baseBetAmount: Long
)

@Serializable
data class UpdateServerSettingsRequest(
    val currencyCode: String,
    val baseBetAmount: Long
)