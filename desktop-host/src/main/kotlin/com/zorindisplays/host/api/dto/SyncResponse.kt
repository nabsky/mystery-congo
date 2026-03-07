package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncResponse(
    val stateVersion: Long,
    val lastEventId: Long,
    val events: List<SyncEventDto>
)

