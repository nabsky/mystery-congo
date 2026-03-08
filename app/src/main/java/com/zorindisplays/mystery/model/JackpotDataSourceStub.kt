package com.zorindisplays.mystery.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class JackpotDataSourceStub : JackpotDataSource {
    override val state: StateFlow<JackpotState> = MutableStateFlow(JackpotState())
    override val events: SharedFlow<JackpotEvent> = MutableSharedFlow()

    override fun start(scope: CoroutineScope) {
        // Заглушка
    }

    override suspend fun stop() {
        // Заглушка
    }

    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        // Заглушка
    }

    override suspend fun confirmBets(tableId: Int) {
        // Заглушка
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        // Заглушка
    }

    override suspend fun confirmPayout(tableId: Int) {
        // Заглушка
    }
}

