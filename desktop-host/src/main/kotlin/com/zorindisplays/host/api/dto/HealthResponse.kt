package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val stateVersion: Long,
    val lastEventId: Long
)

