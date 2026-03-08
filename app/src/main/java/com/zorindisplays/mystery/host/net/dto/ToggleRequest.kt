package com.zorindisplays.mystery.host.net.dto
import kotlinx.serialization.Serializable

@Serializable
data class ToggleRequest(val tableId: Int, val boxId: Int)

