package com.zorindisplays.display.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface JackpotStateDataSource {

    val state: StateFlow<DemoState>

    val events: SharedFlow<DemoEvent>

    fun start(scope: CoroutineScope)

    suspend fun stop()
}