package com.zorindisplays.host.domain.event

data class SyncEvent(
    val eventId: Long,
    val ts: Long,
    val type: SyncEventType,
    val payloadJson: String,
    val stateVersion: Long
)