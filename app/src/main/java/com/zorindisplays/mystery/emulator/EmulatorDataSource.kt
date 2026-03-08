package com.zorindisplays.mystery.emulator

import com.zorindisplays.mystery.model.JackpotEvent
import com.zorindisplays.mystery.model.JackpotState
import com.zorindisplays.mystery.model.JackpotTableControlDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class EmulatorDataSource(val emulator: Emulator) : JackpotTableControlDataSource {
    override val state: StateFlow<JackpotState> get() = emulator.state
    override val events: SharedFlow<JackpotEvent> get() = emulator.events

    override fun start(scope: CoroutineScope) {
        emulator.start()
    }

    override suspend fun stop() {
        emulator.stop()
    }

    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        emulator.onBetPreview(tableId, setOf(boxId))
    }

    override suspend fun confirmBets(tableId: Int) {
        emulator.onBetConfirmed(tableId)
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        // нет прямого метода, можно добавить в Emulator при необходимости
    }

    override suspend fun confirmPayout(tableId: Int) {
        // нет прямого метода, можно добавить в Emulator при необходимости
    }
}