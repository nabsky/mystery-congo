package com.zorindisplays.display.display

import com.zorindisplays.display.model.*
import com.zorindisplays.display.table.TableDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class DisplayDataSource(
    baseUrl: String,
    scope: CoroutineScope
) : JackpotStateDataSource {

    private val delegate =
        TableDataSource(
            baseUrl = baseUrl,
            tableId = -1,
            scope = scope
        )

    override val state: StateFlow<DemoState>
        get() = delegate.state

    override val events: SharedFlow<DemoEvent>
        get() = delegate.events

    override fun start(scope: CoroutineScope) {
        delegate.start(scope)
    }

    override suspend fun stop() {
        delegate.stop()
    }
}