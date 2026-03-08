package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotResponse(
    val state: JackpotStateDto,
    val stateVersion: Long,
    val lastEventId: Long
)