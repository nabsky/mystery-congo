package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminSystemActionResponse(
    val ok: Boolean,
    val message: String
)