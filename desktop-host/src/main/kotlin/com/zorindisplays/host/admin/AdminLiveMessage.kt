package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminLiveMessage(
    val type: String,
    val eventType: String? = null,
    val payloadJson: String? = null,
    val stateVersion: Long? = null,
    val eventId: Long? = null,
    val ts: Long? = null,
    val message: String? = null
)