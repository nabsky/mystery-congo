package com.zorindisplays.host.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotResponse(
    val state: DemoStateDto,
    val stateVersion: Long,
    val lastEventId: Long
)