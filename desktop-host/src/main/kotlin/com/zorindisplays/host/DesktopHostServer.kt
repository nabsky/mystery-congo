package com.zorindisplays.host

import com.zorindisplays.host.model.ConfirmPayoutRequest
import com.zorindisplays.host.model.ConfirmRequest
import com.zorindisplays.host.model.DemoState
import com.zorindisplays.host.model.HealthResponse
import com.zorindisplays.host.model.OkResponse
import com.zorindisplays.host.model.SelectPayoutBoxRequest
import com.zorindisplays.host.model.SyncEvent
import com.zorindisplays.host.model.SyncResponse
import com.zorindisplays.host.model.ToggleRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.slf4j.event.Level
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val TABLE_COUNT = 8
private const val BOX_COUNT = 9
private const val TABLE_ACTIVE_TIMEOUT_MS = 5_000L

private const val START_RUBY = 100_000L
private const val START_GOLD = 20_000L
private const val START_JADE = 5_000L

fun main() {
    val host = InMemoryHostEmulator()

    println("[DesktopHost] Starting on 0.0.0.0:8080")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module(host)
    }.start(wait = true)
}

private fun Application.module(host: InMemoryHostEmulator) {

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    ok = true,
                    stateVersion = host.currentStateVersion(),
                    lastEventId = host.currentLastEventId()
                )
            )
        }

        get("/snapshot") {
            val tableIdParam = call.request.queryParameters["tableId"]?.toIntOrNull()
            if (tableIdParam != null) {
                host.markActive(tableIdParam)
            }
            call.respond(host.getSnapshot())
        }

        get("/sync") {
            val afterEventId = call.request.queryParameters["afterEventId"]?.toLongOrNull() ?: 0L
            val tableIdParam = call.request.queryParameters["tableId"]?.toIntOrNull()
            if (tableIdParam != null) {
                host.markActive(tableIdParam)
            }
            call.respond(host.getSync(afterEventId))
        }

        post("/input/toggle") {
            val req = call.receive<ToggleRequest>()
            host.toggleBox(req.tableId, req.boxId)
            call.respond(OkResponse(ok = true))
        }

        post("/input/confirm") {
            val req = call.receive<ConfirmRequest>()
            host.confirmBets(req.tableId)
            call.respond(OkResponse(ok = true))
        }

        post("/input/payout/selectBox") {
            val req = call.receive<SelectPayoutBoxRequest>()
            host.selectPayoutBox(req.tableId, req.boxId)
            call.respond(OkResponse(ok = true))
        }

        post("/input/payout/confirm") {
            val req = call.receive<ConfirmPayoutRequest>()
            host.confirmPayout(req.tableId)
            call.respond(OkResponse(ok = true))
        }

        // Не нужен Android TABLE, но удобно для ручных тестов
        post("/setActiveTable") {
            val req = call.receive<SetActiveTableRequest>()
            host.markActive(req.tableId)
            call.respond(OkResponse(ok = true))
        }
    }
}

@Serializable
data class SetActiveTableRequest(val tableId: Int)

private class InMemoryHostEmulator {
    private val mutex = Mutex()

    private var stateVersion = 0L
    private var lastEventId = 0L

    private val lastActivity = LongArray(TABLE_COUNT) { 0L }
    private val activeBoxes = Array(TABLE_COUNT) { mutableSetOf<Int>() }
    private val recentBoxes = Array(TABLE_COUNT) { mutableMapOf<Int, Long>() }

    private val events = mutableListOf<SyncEvent>()

    private var demoState = DemoState(
        tables = (0 until TABLE_COUNT).map { tableId ->
            DemoState.Table(
                tableId = tableId,
                activeBoxes = emptySet(),
                recentBoxes = emptySet(),
                isActive = false
            )
        },
        jackpots = mapOf(
            "RUBY" to START_RUBY,
            "GOLD" to START_GOLD,
            "JADE" to START_JADE
        ),
        systemMode = DemoState.SystemMode.ACCEPTING_BETS,
        pendingWin = null
    )

    suspend fun toggleBox(tableId: Int, boxId: Int) = mutex.withLock {
        require(tableId in 0 until TABLE_COUNT) { "Invalid tableId=$tableId" }
        require(boxId in 0 until BOX_COUNT) { "Invalid boxId=$boxId" }
        check(demoState.systemMode == DemoState.SystemMode.ACCEPTING_BETS) {
            "System is not accepting bets"
        }

        markActiveLocked(tableId)

        if (activeBoxes[tableId].contains(boxId)) {
            activeBoxes[tableId].remove(boxId)
        } else {
            if (activeBoxes[tableId].isEmpty()) {
                recentBoxes[tableId].clear()
            }
            activeBoxes[tableId].add(boxId)
        }

        rebuildStateLocked()

        addEventLocked(
            type = "BoxToggled",
            payload = mapOf(
                "tableId" to tableId,
                "boxId" to boxId
            )
        )
    }

    suspend fun confirmBets(tableId: Int) = mutex.withLock {
        require(tableId in 0 until TABLE_COUNT) { "Invalid tableId=$tableId" }
        check(demoState.systemMode == DemoState.SystemMode.ACCEPTING_BETS) {
            "System is not accepting bets"
        }

        markActiveLocked(tableId)

        val selected = activeBoxes[tableId].toList().sorted()
        if (selected.isEmpty()) return

        val now = System.currentTimeMillis()

        // Для desktop теста: всегда делаем win на первом выбранном боксе.
        val jackpotId = "RUBY"
        val winBox = selected.first()
        val winAmount = demoState.jackpots[jackpotId] ?: START_RUBY

        selected.forEach { boxId ->
            recentBoxes[tableId][boxId] = now
        }
        activeBoxes[tableId].clear()

        demoState = demoState.copy(
            systemMode = DemoState.SystemMode.PAYOUT_PENDING,
            pendingWin = DemoState.PendingWin(
                jackpotId = jackpotId,
                tableId = tableId,
                boxId = winBox,
                winAmount = winAmount
            )
        )
        rebuildStateLocked(preserveModeAndPending = true)

        addEventLocked(
            type = "BetsConfirmed",
            payload = mapOf(
                "tableId" to tableId
            )
        )

        addEventLocked(
            type = "JackpotHitDetected",
            payload = mapOf(
                "jackpotId" to jackpotId,
                "tableId" to tableId,
                "boxId" to winBox,
                "winAmount" to winAmount
            )
        )
    }

    suspend fun selectPayoutBox(tableId: Int, boxId: Int) = mutex.withLock {
        require(tableId in 0 until TABLE_COUNT) { "Invalid tableId=$tableId" }
        require(boxId in 0 until BOX_COUNT) { "Invalid boxId=$boxId" }

        markActiveLocked(tableId)

        val pending = demoState.pendingWin ?: error("No pending win")
        check(demoState.systemMode == DemoState.SystemMode.PAYOUT_PENDING) {
            "System is not in PAYOUT_PENDING"
        }
        check(pending.tableId == tableId) {
            "Pending win belongs to table ${pending.tableId}, not $tableId"
        }

        demoState = demoState.copy(
            pendingWin = pending.copy(boxId = boxId)
        )

        addEventLocked(
            type = "PayoutSelectedBox",
            payload = mapOf(
                "tableId" to tableId,
                "boxId" to boxId
            )
        )
    }

    suspend fun confirmPayout(tableId: Int) = mutex.withLock {
        require(tableId in 0 until TABLE_COUNT) { "Invalid tableId=$tableId" }

        markActiveLocked(tableId)

        val pending = demoState.pendingWin ?: error("No pending win")
        check(demoState.systemMode == DemoState.SystemMode.PAYOUT_PENDING) {
            "System is not in PAYOUT_PENDING"
        }
        check(pending.tableId == tableId) {
            "Pending win belongs to table ${pending.tableId}, not $tableId"
        }

        val jackpots = demoState.jackpots.toMutableMap()
        when (pending.jackpotId) {
            "RUBY" -> jackpots["RUBY"] = START_RUBY
            "GOLD" -> jackpots["GOLD"] = START_GOLD
            "JADE" -> jackpots["JADE"] = START_JADE
        }

        demoState = demoState.copy(
            jackpots = jackpots,
            systemMode = DemoState.SystemMode.ACCEPTING_BETS,
            pendingWin = null
        )
        rebuildStateLocked(preserveModeAndPending = true)

        addEventLocked(
            type = "PayoutConfirmed",
            payload = mapOf(
                "tableId" to tableId,
                "boxId" to pending.boxId
            )
        )
    }

    suspend fun markActive(tableId: Int) = mutex.withLock {
        require(tableId in 0 until TABLE_COUNT) { "Invalid tableId=$tableId" }
        markActiveLocked(tableId)
        rebuildStateLocked()
    }

    suspend fun getSnapshot(): DemoState = mutex.withLock {
        rebuildStateLocked()
        demoState
    }

    suspend fun getSync(afterEventId: Long): SyncResponse = mutex.withLock {
        rebuildStateLocked()
        val filtered = events.filter { it.eventId > afterEventId }
        SyncResponse(
            stateVersion = stateVersion,
            lastEventId = lastEventId,
            events = filtered
        )
    }

    suspend fun currentStateVersion(): Long = mutex.withLock { stateVersion }

    suspend fun currentLastEventId(): Long = mutex.withLock { lastEventId }

    private fun markActiveLocked(tableId: Int) {
        lastActivity[tableId] = System.currentTimeMillis()
    }

    private fun rebuildStateLocked(preserveModeAndPending: Boolean = false) {
        val now = System.currentTimeMillis()

        val tables = (0 until TABLE_COUNT).map { tableId ->
            val isActive = now - lastActivity[tableId] < TABLE_ACTIVE_TIMEOUT_MS
            val recent = recentBoxes[tableId]
                .filter { (_, ts) -> now - ts < 60_000L }
                .keys
                .toSet()

            DemoState.Table(
                tableId = tableId,
                activeBoxes = activeBoxes[tableId].toSet(),
                recentBoxes = recent,
                isActive = isActive
            )
        }

        demoState = demoState.copy(
            tables = tables
        )
    }

    private fun addEventLocked(type: String, payload: Map<String, Any>) {
        println("EVENT $type $payload")

        lastEventId += 1
        stateVersion += 1

        events += SyncEvent(
            eventId = lastEventId,
            ts = System.currentTimeMillis(),
            type = type,
            payloadJson = payload.toPayloadJson()
        )

        // чтобы список не рос бесконечно
        if (events.size > 1000) {
            events.removeAt(0)
        }
    }

    private fun Map<String, Any>.toPayloadJson(): String {
        val jsonObj = buildJsonObject {
            forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
        return Json.encodeToString(jsonObj)
    }
}