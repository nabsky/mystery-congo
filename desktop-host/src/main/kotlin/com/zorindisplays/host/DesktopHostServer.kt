package com.zorindisplays.host

import com.zorindisplays.host.model.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun main() {
    val host = InMemoryHostEmulator()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
            get("/snapshot") {
                // Вместо DemoState возвращаем RemoteSnapshot
                call.respond(host.getRemoteSnapshot())
            }
            get("/sync") {
                val afterEventId = call.request.queryParameters["afterEventId"]?.toLongOrNull() ?: 0L
                call.respond(host.getSync(afterEventId))
            }
            post("/input/toggle") {
                val req = call.receive<ToggleRequest>()
                host.toggleBox(req.tableId, req.boxId)
                call.respond(mapOf("ok" to true))
            }
            post("/input/confirm") {
                val req = call.receive<ConfirmRequest>()
                host.confirmBets(req.tableId)
                call.respond(mapOf("ok" to true))
            }
            post("/input/payout/selectBox") {
                val req = call.receive<SelectPayoutBoxRequest>()
                host.selectPayoutBox(req.tableId, req.boxId)
                call.respond(mapOf("ok" to true))
            }
            post("/input/payout/confirm") {
                val req = call.receive<ConfirmPayoutRequest>()
                host.confirmPayout(req.tableId)
                call.respond(mapOf("ok" to true))
            }
        }
    }.start(wait = true)
}

class InMemoryHostEmulator {
    private val mutex = Mutex()
    private var stateVersion = 1L
    private var lastEventId = 1L
    private var demoState = DemoState(
        tables = (0..7).map { DemoState.Table(tableId = it, activeBoxes = emptySet(), recentBoxes = emptySet(), isActive = true) },
        jackpots = mapOf("RUBY" to 1000L, "GOLD" to 2000L, "JADE" to 3000L),
        systemMode = DemoState.SystemMode.ACCEPTING_BETS,
        pendingWin = null
    )
    private val events = mutableListOf<SyncEvent>()

    suspend fun toggleBox(tableId: Int, boxId: Int) = mutex.withLock {
        val table = demoState.tables.find { it.tableId == tableId } ?: return
        val newActive = if (table.activeBoxes.contains(boxId)) {
            table.activeBoxes - boxId
        } else {
            table.activeBoxes + boxId
        }
        val updatedTable = table.copy(activeBoxes = newActive)
        demoState = demoState.copy(
            tables = demoState.tables.map { if (it.tableId == tableId) updatedTable else it }
        )
        addEvent("BoxToggled", mapOf("tableId" to tableId, "boxId" to boxId))
    }

    suspend fun confirmBets(tableId: Int) = mutex.withLock {
        demoState = demoState.copy(systemMode = DemoState.SystemMode.PAYOUT_PENDING)
        addEvent("BetsConfirmed", mapOf("tableId" to tableId))
    }

    suspend fun selectPayoutBox(tableId: Int, boxId: Int) = mutex.withLock {
        demoState = demoState.copy(pendingWin = DemoState.PendingWin(
            jackpotId = "RUBY",
            tableId = tableId,
            boxId = boxId,
            winAmount = 1000L
        ))
        addEvent("PayoutSelectedBox", mapOf("tableId" to tableId, "boxId" to boxId))
    }

    suspend fun confirmPayout(tableId: Int) = mutex.withLock {
        demoState = demoState.copy(pendingWin = null, systemMode = DemoState.SystemMode.ACCEPTING_BETS)
        addEvent("PayoutConfirmed", mapOf("tableId" to tableId))
    }

    fun getSnapshot(): DemoState = demoState

    fun getRemoteSnapshot(): RemoteSnapshot {
        // Преобразуем DemoState в RemoteSnapshot (1-based id, boxes, status)
        return RemoteSnapshot(
            tables = demoState.tables.map { table ->
                RemoteTable(
                    id = table.tableId + 1, // 1-based
                    boxes = (0..8).map { boxId ->
                        RemoteBox(
                            id = boxId + 1, // 1-based
                            isSelected = table.activeBoxes.contains(boxId)
                        )
                    },
                    status = if (table.isActive) "BETTING" else "OFFLINE"
                )
            },
            jackpots = demoState.jackpots,
            systemMode = demoState.systemMode.name
        )
    }

    fun getSync(afterEventId: Long): SyncResponse {
        val filtered = events.filter { it.eventId > afterEventId }
        return SyncResponse(
            stateVersion = stateVersion,
            lastEventId = lastEventId,
            events = filtered
        )
    }

    private fun mapToJson(map: Map<String, Any>): String {
        val jsonObj = buildJsonObject {
            map.forEach { (k, v) ->
                when (v) {
                    is Number -> put(k, JsonPrimitive(v))
                    is String -> put(k, JsonPrimitive(v))
                    is Boolean -> put(k, JsonPrimitive(v))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
        return Json.encodeToString(jsonObj)
    }

    private fun addEvent(type: String, payload: Map<String, Any>) {
        val eventId = ++lastEventId
        val ts = System.currentTimeMillis()
        val payloadJson = mapToJson(payload)
        events.add(
            SyncEvent(
                eventId = eventId,
                ts = ts,
                type = type,
                payloadJson = payloadJson
            )
        )
    }
}
