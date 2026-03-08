package com.zorindisplays.mystery.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface JackpotDataSource {
    val state: StateFlow<JackpotState>
    val events: SharedFlow<JackpotEvent>
    fun start(scope: CoroutineScope)
    suspend fun stop()
    suspend fun toggleBox(tableId: Int, boxId: Int)
    suspend fun confirmBets(tableId: Int)
    suspend fun selectPayoutBox(tableId: Int, boxId: Int)
    suspend fun confirmPayout(tableId: Int)
}