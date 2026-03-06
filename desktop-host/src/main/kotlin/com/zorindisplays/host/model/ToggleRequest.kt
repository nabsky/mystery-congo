package com.zorindisplays.host.model

import kotlinx.serialization.Serializable

@Serializable
data class ToggleRequest(val tableId: Int, val boxId: Int)

