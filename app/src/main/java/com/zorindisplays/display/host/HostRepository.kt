package com.zorindisplays.display.host

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.zorindisplays.display.host.db.*
import com.zorindisplays.display.model.DemoState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

class HostRepository(context: Context) {
    private val db = HostDatabase.getInstance(context)
    private val dao = db.hostDao()

    suspend fun appendEvent(type: String, payloadJson: String): Long {
        val event = EventLogRow(
            ts = System.currentTimeMillis(),
            type = type,
            payloadJson = payloadJson
        )
        return dao.appendEvent(event)
    }

    suspend fun getEventsAfter(eventId: Long): List<EventLogRow> =
        dao.getEventsAfter(eventId)

    suspend fun getJackpotState(): Map<String, Long> =
        dao.getJackpotState().associate { it.jackpotId to it.currentAmount }

    suspend fun setJackpotState(jackpots: Map<String, Long>) {
        val now = System.currentTimeMillis()
        val rows = jackpots.map { (id, amount) ->
            JackpotStateRow(jackpotId = id, currentAmount = amount, updatedAt = now)
        }
        dao.setJackpotState(*rows.toTypedArray())
    }

    suspend fun getGlobalState(): GlobalStateRow? = dao.getGlobalState()

    suspend fun bumpStateVersionAndLastEventId(newLastEventId: Long) {
        val now = System.currentTimeMillis()
        dao.bumpStateVersionAndLastEventId(newLastEventId, now)
    }

    suspend fun ensureInitialized() {
        val state = dao.getGlobalState()
        if (state == null) {
            val now = System.currentTimeMillis()
            db.withTransaction {
                dao.insertGlobalState(GlobalStateRow(id = 1, stateVersion = 0, lastEventId = 0, updatedAt = now))
            }
        }
    }

    suspend fun applyEvent(type: String, payloadJson: String, jackpotUpdates: Map<String, Long>? = null) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            ensureInitialized()
            val event = EventLogRow(ts = now, type = type, payloadJson = payloadJson)
            val eventId = dao.appendEvent(event)
            jackpotUpdates?.let {
                val rows = it.map { (id, amount) -> JackpotStateRow(jackpotId = id, currentAmount = amount, updatedAt = now) }
                dao.setJackpotState(*rows.toTypedArray())
            }
            dao.bumpStateVersionAndLastEventId(eventId, now)
        }
    }

    // Удаляем replayEvents, snapshot теперь возвращает текущее состояние
    suspend fun snapshot(): DemoState {
        val jackpots = dao.getJackpotState().associate { it.jackpotId to it.currentAmount }
        return DemoState(
            tables = emptyList(),
            jackpots = jackpots,
            systemMode = DemoState.SystemMode.ACCEPTING_BETS,
            pendingWin = null
        )
    }
}
