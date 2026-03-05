package com.zorindisplays.display.table

import com.zorindisplays.display.model.JackpotDataSource
import com.zorindisplays.display.model.DemoState
import com.zorindisplays.display.model.DemoEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TableDataSource(
    private val baseUrl: String,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 500
) : JackpotDataSource {
    private val _state = MutableStateFlow(DemoState())
    override val state: StateFlow<DemoState> get() = _state.asStateFlow()

    private val _events = MutableSharedFlow<DemoEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<DemoEvent> get() = _events.asSharedFlow()

    enum class ConnectionState { CONNECTING, CONNECTED, OFFLINE }
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> get() = _connectionState.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val lastEventId = AtomicLong(0)
    private var pollingJob: Job? = null

    private var lastSuccessfulSyncTime = 0L
    private val _isHostOnline = MutableStateFlow(true)
    val isHostOnline: StateFlow<Boolean> get() = _isHostOnline.asStateFlow()

    init {
        scope.launch {
            try {
                // GET /health (опционально)
                runCatching {
                    client.get("$baseUrl/health")
                }
                // GET /snapshot
                val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                _state.value = snapshot
                lastEventId.set(0L)
                _connectionState.value = ConnectionState.CONNECTED
                lastSuccessfulSyncTime = System.currentTimeMillis()
                _isHostOnline.value = true
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.OFFLINE
                _isHostOnline.value = false
            }
            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var backoffMs = pollIntervalMs
            while (isActive) {
                val jitter = Random.nextLong(0, 150)
                delay(backoffMs + jitter)
                val now = System.currentTimeMillis()
                if (now - lastSuccessfulSyncTime > 5000) {
                    _isHostOnline.value = false
                }
                try {
                    val afterId = lastEventId.get()
                    val resp: SyncResponse = client.get("$baseUrl/sync?afterEventId=$afterId").body()
                    if (_connectionState.value != ConnectionState.CONNECTED) {
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                    lastSuccessfulSyncTime = System.currentTimeMillis()
                    _isHostOnline.value = true
                    if (resp.events.isNotEmpty()) {
                        resp.events.sortedBy { it.eventId }.forEach { event ->
                            lastEventId.set(maxOf(lastEventId.get(), event.eventId))
                            when (event.type) {
                                "JackpotHitDetected" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val jackpotId = obj["jackpotId"]?.jsonPrimitive?.content ?: ""
                                    val tableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                    val boxId = obj["boxId"]?.jsonPrimitive?.int ?: 0
                                    val winAmount = obj["winAmount"]?.jsonPrimitive?.long ?: 0L
                                    _events.tryEmit(DemoEvent.JackpotHit(jackpotId, tableId, boxId, winAmount))
                                }
                                "BetsConfirmed", "PayoutConfirmed" -> {
                                    // Просто рефетчим /snapshot
                                    val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                                    _state.value = snapshot
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }
                                else -> {}
                            }
                        }
                    }
                    backoffMs = pollIntervalMs
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.OFFLINE
                    _isHostOnline.value = false
                    backoffMs = when (backoffMs) {
                        in 0..999 -> 1000
                        in 1000..1999 -> 2000
                        else -> 5000
                    }
                }
            }
        }
    }

    override fun start(scope: CoroutineScope) { /* no-op */ }

    override suspend fun stop() {
        pollingJob?.cancel()
        client.close()
    }

    // Input команды
    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
            client.post("$baseUrl/input/toggle") {
                setBody(ToggleRequest(tableId = tableId, boxId = boxId))
            }
        }
    }

    override suspend fun confirmBets(tableId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
            client.post("$baseUrl/input/confirm") {
                setBody(ConfirmRequest(tableId = tableId))
            }
        }
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
            client.post("$baseUrl/input/payout/selectBox") {
                setBody(SelectPayoutBoxRequest(tableId = tableId, boxId = boxId))
            }
        }
    }

    override suspend fun confirmPayout(tableId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
            client.post("$baseUrl/input/payout/confirm") {
                setBody(ConfirmPayoutRequest(tableId = tableId))
            }
        }
    }
    @Serializable
    data class SyncResponse(
        val stateVersion: Long = 0,
        val lastEventId: Long = 0,
        val events: List<SyncEvent> = emptyList()
    )

    @Serializable
    data class SyncEvent(
        val eventId: Long,
        val ts: Long,
        val type: String,
        val payloadJson: String
    )

    @Serializable
    data class ToggleRequest(val tableId: Int, val boxId: Int)

    @Serializable
    data class ConfirmRequest(val tableId: Int)

    @Serializable
    data class SelectPayoutBoxRequest(val tableId: Int, val boxId: Int)

    @Serializable
    data class ConfirmPayoutRequest(val tableId: Int)
}
