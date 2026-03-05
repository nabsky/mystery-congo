package com.zorindisplays.display

import com.zorindisplays.display.DeviceRole

data class DeviceConfig(
    val role: DeviceRole = DeviceRole.DEMO,
    val hostUrl: String = "http://127.0.0.1:8080"
)

