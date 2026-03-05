package com.zorindisplays.display.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface JackpotDataSource {
    val state: StateFlow<DemoState>
    val events: SharedFlow<DemoEvent>
    fun start(scope: CoroutineScope)
    suspend fun stop()
    suspend fun toggleBox(tableId: Int, boxId: Int)
    suspend fun confirmBets(tableId: Int)
    suspend fun selectPayoutBox(tableId: Int, boxId: Int)
    suspend fun confirmPayout(tableId: Int)
}

data class DeviceConfig(
    val role: DeviceRole = DeviceRole.DEMO,
    val hostUrl: String = "http://127.0.0.1:8080"
)