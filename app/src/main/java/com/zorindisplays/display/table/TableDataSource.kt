package com.zorindisplays.display.table

import android.util.Log
import com.zorindisplays.display.model.DemoEvent
import com.zorindisplays.display.model.DemoState
import com.zorindisplays.display.model.JackpotDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TableDataSource(
    private val baseUrl: String,
    private val tableId: Int,
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

    private val _isHostOnline = MutableStateFlow(true)
    val isHostOnline: StateFlow<Boolean> get() = _isHostOnline.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val lastEventId = AtomicLong(0)
    private var pollingJob: Job? = null
    private var lastSuccessfulSyncTime = 0L

    init {
        scope.launch {
            Log.d("TableDataSource", "Initializing connection to $baseUrl with tableId=$tableId")
            try {
                runCatching {
                    val healthResp = client.get("$baseUrl/health")
                    Log.d("TableDataSource", "Health check status: ${healthResp.status}")
                }.onFailure {
                    Log.w("TableDataSource", "Health check failed: ${it.message}")
                }

                val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                Log.d("TableDataSource", "Snapshot received: $snapshot")

                _state.value = snapshot
                lastEventId.set(0L)
                _connectionState.value = ConnectionState.CONNECTED
                lastSuccessfulSyncTime = System.currentTimeMillis()
                _isHostOnline.value = true
            } catch (e: Exception) {
                Log.e("TableDataSource", "Initialization failed", e)
                _connectionState.value = ConnectionState.OFFLINE
                _isHostOnline.value = false
            }

            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            Log.d("TableDataSource", "Starting polling loop")
            var backoffMs = pollIntervalMs

            while (isActive) {
                val jitter = Random.nextLong(0, 150)
                delay(backoffMs + jitter)

                val now = System.currentTimeMillis()
                if (now - lastSuccessfulSyncTime > 5000) {
                    if (_isHostOnline.value) {
                        Log.w("TableDataSource", "Host seems offline (timeout)")
                    }
                    _isHostOnline.value = false
                }

                try {
                    val afterId = lastEventId.get()
                    val resp: SyncResponse = client.get("$baseUrl/sync?afterEventId=$afterId").body()

                    if (_connectionState.value != ConnectionState.CONNECTED) {
                        Log.i("TableDataSource", "Reconnected to host")
                        _connectionState.value = ConnectionState.CONNECTED
                    }

                    lastSuccessfulSyncTime = System.currentTimeMillis()
                    _isHostOnline.value = true

                    if (resp.events.isNotEmpty()) {
                        Log.d("TableDataSource", "Received ${resp.events.size} events")

                        resp.events.sortedBy { it.eventId }.forEach { event ->
                            lastEventId.set(maxOf(lastEventId.get(), event.eventId))

                            when (event.type) {
                                "BoxToggled" -> {
                                    try {
                                        val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                        val eventTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                        val eventBoxId = obj["boxId"]?.jsonPrimitive?.int ?: 0

                                        val current = _state.value
                                        val newTables = current.tables.map { t ->
                                            if (t.tableId == eventTableId) {
                                                val newActive =
                                                    if (t.activeBoxes.contains(eventBoxId)) {
                                                        t.activeBoxes - eventBoxId
                                                    } else {
                                                        t.activeBoxes + eventBoxId
                                                    }
                                                t.copy(activeBoxes = newActive)
                                            } else {
                                                t
                                            }
                                        }
                                        _state.value = current.copy(tables = newTables)
                                    } catch (e: Exception) {
                                        Log.e("TableDataSource", "Error processing BoxToggled", e)
                                        val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                                        _state.value = snapshot
                                    }
                                }

                                "BetsConfirmed" -> {
                                    val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                                    _state.value = snapshot
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }

                                "JackpotHitDetected" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val jackpotId = obj["jackpotId"]?.jsonPrimitive?.content ?: ""
                                    val eventTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                    val eventBoxId = obj["boxId"]?.jsonPrimitive?.int ?: 0
                                    val winAmount = obj["winAmount"]?.jsonPrimitive?.long ?: 0L

                                    _events.tryEmit(
                                        DemoEvent.JackpotHit(
                                            jackpotId = jackpotId,
                                            tableId = eventTableId,
                                            boxId = eventBoxId,
                                            winAmount = winAmount
                                        )
                                    )
                                }

                                "PayoutSelectedBox" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val eventTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                    val eventBoxId = obj["boxId"]?.jsonPrimitive?.int ?: 0

                                    _events.tryEmit(
                                        DemoEvent.DealerPayoutBoxSelected(
                                            tableId = eventTableId,
                                            boxId = eventBoxId
                                        )
                                    )
                                }

                                "PayoutConfirmed" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val eventTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                    val eventBoxId = obj["boxId"]?.jsonPrimitive?.int ?: 0

                                    _events.tryEmit(
                                        DemoEvent.DealerPayoutConfirmed(
                                            tableId = eventTableId,
                                            boxId = eventBoxId
                                        )
                                    )

                                    val snapshot: DemoState = client.get("$baseUrl/snapshot").body()
                                    _state.value = snapshot
                                }

                                else -> {
                                    Log.d("TableDataSource", "Ignoring unknown event type: ${event.type}")
                                }
                            }
                        }
                    }

                    backoffMs = pollIntervalMs
                } catch (e: Exception) {
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        Log.e("TableDataSource", "Polling error", e)
                    }
                    _connectionState.value = ConnectionState.OFFLINE
                    _isHostOnline.value = false
                }
            }
        }
    }

    override fun start(scope: CoroutineScope) {
        Log.d("TableDataSource", "start() called for tableId=$tableId")
    }

    override suspend fun stop() {
        Log.d("TableDataSource", "stop() called for tableId=$tableId")
        pollingJob?.cancel()
        client.close()
    }

    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        if (this.tableId != tableId) {
            Log.w("TableDataSource", "toggleBox refused: localId=${this.tableId} vs reqId=$tableId")
            return
        }

        try {
            val req = ToggleRequest(tableId = tableId, boxId = boxId)
            Log.d("TableDataSource", "Sending toggle: $req")
            client.post("$baseUrl/input/toggle") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        } catch (e: Exception) {
            Log.e("TableDataSource", "Failed to toggle box", e)
        }
    }

    override suspend fun confirmBets(tableId: Int) {
        if (this.tableId != tableId) {
            Log.w("TableDataSource", "confirmBets refused: localId=${this.tableId} vs reqId=$tableId")
            return
        }

        try {
            val req = ConfirmRequest(tableId = tableId)
            Log.d("TableDataSource", "Sending confirm: $req")
            client.post("$baseUrl/input/confirm") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        } catch (e: Exception) {
            Log.e("TableDataSource", "Failed to confirm bets", e)
        }
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return

        runCatching {
            client.post("$baseUrl/input/payout/selectBox") {
                contentType(ContentType.Application.Json)
                setBody(SelectPayoutBoxRequest(tableId = tableId, boxId = boxId))
            }
        }.onFailure {
            Log.e("TableDataSource", "Failed to select payout box", it)
        }
    }

    override suspend fun confirmPayout(tableId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return

        runCatching {
            client.post("$baseUrl/input/payout/confirm") {
                contentType(ContentType.Application.Json)
                setBody(ConfirmPayoutRequest(tableId = tableId))
            }
        }.onFailure {
            Log.e("TableDataSource", "Failed to confirm payout", it)
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