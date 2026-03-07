package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ToggleRequest(val tableId: Int, val boxId: Int)

