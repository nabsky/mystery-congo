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
import io.ktor.http.ContentType
import io.ktor.http.contentType
import android.util.Log

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

    // DTOs for server structure match
    @Serializable
    private data class RemoteBox(
        val id: Int,
        val isSelected: Boolean
    )

    @Serializable
    private data class RemoteTable(
        val id: Int, // Mapped to tableId
        val boxes: List<RemoteBox> = emptyList(),
        val status: String = "BETTING"
    )

    @Serializable
    private data class RemoteSnapshot(
        val tables: List<RemoteTable> = emptyList(),
        val jackpots: Map<String, Long> = emptyMap(),
        val systemMode: String = "ACCEPTING_BETS"
    )

    private fun RemoteSnapshot.toDomain(): DemoState {
        return DemoState(
            tables = this.tables.map { rt ->
                DemoState.Table(
                    // Server uses 1-based IDs (1..8), Domain/UI uses 0-based (0..7)
                    tableId = rt.id - 1,
                    // Server uses 1-based IDs (1..9), Domain/UI uses 0-based (0..8)
                    activeBoxes = rt.boxes.filter { it.isSelected }.map { it.id - 1 }.toSet(),
                    isActive = rt.status == "BETTING" || rt.status == "CONFIRMED"
                )
            },
            jackpots = this.jackpots,
            systemMode = try {
                DemoState.SystemMode.valueOf(this.systemMode)
            } catch (e: Exception) {
                DemoState.SystemMode.ACCEPTING_BETS
            }
        )
    }

    init {
        scope.launch {
            Log.d("TableDataSource", "Initializing connection to $baseUrl with tableId=$tableId")
            try {
                // GET /health (опционально)
                runCatching {
                    Log.d("TableDataSource", "Checking health at $baseUrl/health")
                    val healthResp = client.get("$baseUrl/health")
                    Log.d("TableDataSource", "Health check status: ${healthResp.status}")
                }.onFailure {
                    Log.w("TableDataSource", "Health check failed: ${it.message}")
                }
                // GET /snapshot
                Log.d("TableDataSource", "Fetching snapshot from $baseUrl/snapshot")
                val remoteSnapshot: RemoteSnapshot = client.get("$baseUrl/snapshot?tableId=$tableId").body()
                val snapshot = remoteSnapshot.toDomain()
                Log.d("TableDataSource", "Snapshot received & parsed: $snapshot")
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
                     if (_isHostOnline.value) Log.w("TableDataSource", "Host seems offline (timeout)")
                    _isHostOnline.value = false
                }
                try {
                    val afterId = lastEventId.get()
                    // Log.v("TableDataSource", "Syncing events after $afterId") // verbose logging if needed
                    val resp: SyncResponse = client.get("$baseUrl/sync?afterEventId=$afterId&tableId=$tableId").body()
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
                                        // Server sent 1-based IDs
                                        val serverTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                        val serverBoxId = obj["boxId"]?.jsonPrimitive?.int ?: 0

                                        val tId = serverTableId - 1
                                        val bId = serverBoxId - 1

                                        // Update local state
                                        val current = _state.value
                                        val newTables = current.tables.map { t ->
                                            if (t.tableId == tId) {
                                                val newActive = if (t.activeBoxes.contains(bId)) {
                                                    t.activeBoxes - bId
                                                } else {
                                                    t.activeBoxes + bId
                                                }
                                                t.copy(activeBoxes = newActive)
                                            } else t
                                        }
                                        _state.value = current.copy(tables = newTables)
                                    } catch(e: Exception) {
                                        Log.e("TableDataSource", "Error processing BoxToggled", e)
                                        // Fallback
                                        val rs: RemoteSnapshot = client.get("$baseUrl/snapshot?tableId=$tableId").body()
                                        _state.value = rs.toDomain()
                                    }
                                }
                                "TableConfirmed" -> {
                                     val rs: RemoteSnapshot = client.get("$baseUrl/snapshot?tableId=$tableId").body()
                                    _state.value = rs.toDomain()
                                }
                                "JackpotHitDetected" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val jackpotId = obj["jackpotId"]?.jsonPrimitive?.content ?: ""
                                    // Server 1-based -> Domain 0-based
                                    val tableId = (obj["tableId"]?.jsonPrimitive?.int ?: 0) - 1
                                    val boxId = (obj["boxId"]?.jsonPrimitive?.int ?: 0) - 1
                                    val winAmount = obj["winAmount"]?.jsonPrimitive?.long ?: 0L
                                    _events.tryEmit(DemoEvent.JackpotHit(jackpotId, tableId, boxId, winAmount))
                                }
                                "PayoutSelectedBox" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val tableId = (obj["tableId"]?.jsonPrimitive?.int ?: 0) - 1
                                    val boxId = (obj["boxId"]?.jsonPrimitive?.int ?: 0) - 1
                                    _events.tryEmit(DemoEvent.DealerPayoutBoxSelected(tableId, boxId))
                                }
                                "PayoutConfirmed" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val tableId = (obj["tableId"]?.jsonPrimitive?.int ?: 0) - 1
                                    val boxId = (obj["boxId"]?.jsonPrimitive?.int ?: 0) - 1 // boxId might not be present or 0, check payload

                                     // NOTE: PayoutConfirmed payload might not have boxId, usually just tableId.
                                     // If code assumes boxId, it defaults to -1 (0-1).
                                    _events.tryEmit(DemoEvent.DealerPayoutConfirmed(tableId, boxId))

                                    // Also refresh snapshot just in case state changed
                                    val rs: RemoteSnapshot = client.get("$baseUrl/snapshot?tableId=$tableId").body()
                                    _state.value = rs.toDomain()
                                }
                                "BetsConfirmed" -> {
                                    // Просто рефетчим /snapshot
                                    val rs: RemoteSnapshot = client.get("$baseUrl/snapshot?tableId=$tableId").body()
                                    _state.value = rs.toDomain()
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }
                                else -> {}
                            }
                        }
                    }
                    backoffMs = pollIntervalMs
                } catch (e: Exception) {
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        Log.e("TableDataSource", "Polling error: ${e.message}")
                    }
                    // Exponential backoff or just keep trying?
                    // delay(backoffMs) // optionally increase backoff
                }
            }
        }
    }

    override fun start(scope: CoroutineScope) { /* no-op */ }

    override suspend fun stop() {
        pollingJob?.cancel()
        client.close()
    }

    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        if (this.tableId != tableId) {
             Log.w("TableDataSource", "toggleBox refused: localId=${this.tableId} vs reqId=$tableId")
             return
        }
        try {
            // Client 0-based -> Server 1-based
            val req = ToggleRequest(tableId = tableId + 1, boxId = boxId + 1)
            Log.d("TableDataSource", "Sending toggle: $req")
            client.post("$baseUrl/input/toggle") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        } catch (e: Exception) {
            Log.e("TableDataSource", "Failed to toggle box", e)
            e.printStackTrace()
        }
    }

    override suspend fun confirmBets(tableId: Int) {
        if (this.tableId != tableId) {
             Log.w("TableDataSource", "confirmBets refused: localId=${this.tableId} vs reqId=$tableId")
             return
        }
        try {
            val req = ConfirmRequest(tableId = tableId + 1)
            Log.d("TableDataSource", "Sending confirm: $req")
            client.post("$baseUrl/input/confirm") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        } catch (e: Exception) {
             Log.e("TableDataSource", "Failed to confirm bets", e)
            e.printStackTrace()
        }
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
             // Client 0-based -> Server 1-based
            client.post("$baseUrl/input/payout/selectBox") {
                contentType(ContentType.Application.Json) // Ensure content type
                setBody(SelectPayoutBoxRequest(tableId = tableId + 1, boxId = boxId + 1))
            }
        }
    }

    override suspend fun confirmPayout(tableId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        runCatching {
             // Client 0-based -> Server 1-based
            client.post("$baseUrl/input/payout/confirm") {
                contentType(ContentType.Application.Json) // Ensure content type
                setBody(ConfirmPayoutRequest(tableId = tableId + 1))
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
