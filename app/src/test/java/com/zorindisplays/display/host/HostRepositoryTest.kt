package com.zorindisplays.display.host

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

class HostRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun smokeTestRepository() = runBlocking {
        val repo = HostRepository(context)
        val payload = buildJsonObject { put("test", "value") }
        val eventId = repo.appendEvent("TEST_EVENT", payload)
        val events = repo.getEventsAfter(eventId - 1)
        assertTrue(events.any { it.eventId == eventId })
        val jackpots = mapOf("RUBY" to 1000L, "GOLD" to 2000L, "JADE" to 3000L)
        repo.setJackpotState(jackpots)
        val state = repo.getJackpotState()
        assertTrue(state["RUBY"] == 1000L)
        assertTrue(state["GOLD"] == 2000L)
        assertTrue(state["JADE"] == 3000L)
    }
}

