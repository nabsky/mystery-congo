package com.zorindisplays.mystery.display

import com.zorindisplays.mystery.model.*
import com.zorindisplays.mystery.table.TableDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class DisplayDataSource(
    baseUrl: String,
    deviceId: String,
    scope: CoroutineScope
) : JackpotStateDataSource {

    private val delegate =
        TableDataSource(
            baseUrl = baseUrl,
            tableId = -1,
            deviceId = deviceId,
            scope = scope
        )

    override val state: StateFlow<JackpotState>
        get() = delegate.state

    override val events: SharedFlow<JackpotEvent>
        get() = delegate.events

    override fun start(scope: CoroutineScope) {
        delegate.start(scope)
    }

    override suspend fun stop() {
        delegate.stop()
    }
}