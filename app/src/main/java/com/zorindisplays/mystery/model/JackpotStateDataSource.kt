package com.zorindisplays.mystery.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface JackpotStateDataSource {

    val state: StateFlow<JackpotState>

    val events: SharedFlow<JackpotEvent>

    fun start(scope: CoroutineScope)

    suspend fun stop()
}