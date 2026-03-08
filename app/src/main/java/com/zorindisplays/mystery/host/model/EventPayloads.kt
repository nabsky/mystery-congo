package com.zorindisplays.mystery.host.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BetPacketPayload(
    val tableId: Int,
    val boxIds: List<Int>
)

@Serializable
data class JackpotWinPayload(
    val jackpotId: String,
    val tableId: Int,
    val boxId: Int,
    val amountWon: Long
)

// Можно расширять для других событий

