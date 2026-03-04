package com.zorindisplays.display.host

import android.content.Context
import androidx.room.Room
import com.zorindisplays.display.host.db.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

class HostRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context,
        HostDatabase::class.java,
        "host_db"
    ).build()
    private val dao = db.hostDao()

    suspend fun appendEvent(type: String, payload: JsonObject): Long {
        val event = EventRow(
            ts = System.currentTimeMillis(),
            type = type,
            payloadJson = Json.encodeToString(JsonObject.serializer(), payload)
        )
        return dao.appendEvent(event)
    }

    suspend fun getEventsAfter(eventId: Long, limit: Int = 500): List<EventRow> =
        dao.getEventsAfter(eventId, limit)

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
}

