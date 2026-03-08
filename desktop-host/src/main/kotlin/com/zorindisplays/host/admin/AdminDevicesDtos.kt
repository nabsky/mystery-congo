package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class DeviceHeartbeatRequest(
    val deviceId: String,
    val deviceType: String,   // DISPLAY or TABLE
    val deviceName: String? = null,
    val tableId: Int? = null,
    val appVersion: String? = null,
    val lastStateVersion: Long,
    val lastEventId: Long
)

@Serializable
data class DeviceHeartbeatResponse(
    val ok: Boolean
)

@Serializable
data class AdminDevicePresenceDto(
    val deviceId: String,
    val deviceType: String,
    val deviceName: String?,
    val tableId: Int?,
    val appVersion: String?,
    val lastStateVersion: Long,
    val lastEventId: Long,
    val lastSeenAt: Long,
    val isOnline: Boolean
)

@Serializable
data class AdminDevicePresenceSummaryDto(
    val displaysOnline: Int,
    val tablesOnline: Int,
    val devices: List<AdminDevicePresenceDto>
)