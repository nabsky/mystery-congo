package com.zorindisplays.display.model

import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.emulator.DemoEvent
import com.zorindisplays.display.emulator.DemoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class EmulatorDataSource(val emulator: Emulator) {
    val state: StateFlow<DemoState> get() = emulator.state
    val events: SharedFlow<DemoEvent> get() = emulator.events

    fun start(scope: CoroutineScope) {
        emulator.start()
    }

    suspend fun stop() {
        emulator.stop()
    }

    suspend fun toggleBox(tableId: Int, boxId: Int) {
        emulator.onBetPreview(tableId, setOf(boxId))
    }

    suspend fun confirmBets(tableId: Int) {
        emulator.onBetConfirmed(tableId)
    }

    suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        // нет прямого метода, можно добавить в Emulator при необходимости
    }

    suspend fun confirmPayout(tableId: Int) {
        // нет прямого метода, можно добавить в Emulator при необходимости
    }
}
