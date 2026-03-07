package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SelectPayoutBoxRequest(val tableId: Int, val boxId: Int)

