package com.zorindisplays.host.model

import kotlinx.serialization.Serializable

@Serializable
data class SelectPayoutBoxRequest(val tableId: Int, val boxId: Int)

