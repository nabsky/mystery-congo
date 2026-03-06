package com.zorindisplays.host.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncResponse(
    val stateVersion: Long,
    val lastEventId: Long,
    val events: List<SyncEvent>
)

@Serializable
data class SyncEvent(
    val eventId: Long,
    val ts: Long,
    val type: String,
    val payloadJson: String
)

@Serializable
data class OkResponse(val ok: Boolean)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val stateVersion: Long,
    val lastEventId: Long
)

