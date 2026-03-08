package com.zorindisplays.mystery.host.net.dto
import kotlinx.serialization.Serializable

@Serializable
data class SelectPayoutBoxRequest(val tableId: Int, val boxId: Int)

