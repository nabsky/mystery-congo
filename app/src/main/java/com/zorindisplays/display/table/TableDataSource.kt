package com.zorindisplays.display.table

import android.util.Log
import com.zorindisplays.display.model.DemoEvent
import com.zorindisplays.display.model.DemoState
import com.zorindisplays.display.model.JackpotDataSource
import com.zorindisplays.display.model.JackpotTableControlDataSource
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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
) : JackpotTableControlDataSource {

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

    private fun apiTableId(internalTableId: Int): Int = internalTableId + 1
    private fun apiBoxId(internalBoxId: Int): Int = internalBoxId + 1
    private val apiTableId: Int get() = tableId + 1

    private fun healthUrl(): String = "$baseUrl/health?tableId=$apiTableId"
    private fun snapshotUrl(): String = "$baseUrl/snapshot?tableId=$apiTableId"
    private fun syncUrl(afterEventId: Long): String =
        "$baseUrl/sync?afterEventId=$afterEventId&tableId=$apiTableId"

    private fun internalTableId(apiTableId: Int): Int = apiTableId - 1
    private fun internalBoxId(apiBoxId: Int): Int = apiBoxId - 1

    private fun DemoState.toInternal(): DemoState {
        return copy(
            tables = tables.map { table ->
                table.copy(
                    tableId = internalTableId(table.tableId),
                    activeBoxes = table.activeBoxes.map { internalBoxId(it) }.toSet(),
                    recentBoxes = table.recentBoxes.map { internalBoxId(it) }.toSet()
                )
            },
            pendingWin = pendingWin?.copy(
                tableId = internalTableId(pendingWin.tableId),
                boxId = internalBoxId(pendingWin.boxId)
            )
        )
    }

    private var lastSnapshotRefreshTime = 0L
    private suspend fun refreshSnapshot() {
        val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
        _state.value = snapshot.state.toInternal()
        lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
        lastSuccessfulSyncTime = System.currentTimeMillis()
        _isHostOnline.value = true
        lastSnapshotRefreshTime = System.currentTimeMillis()
    }

    init {
        scope.launch {
            Log.d("TableDataSource", "Initializing connection to $baseUrl with tableId=$tableId")
            try {
                runCatching {
                    val healthResp = client.get(healthUrl())
                    Log.d("TableDataSource", "Health check status: ${healthResp.status}")
                }.onFailure {
                    Log.w("TableDataSource", "Health check failed: ${it.message}")
                }

                val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                Log.d("TableDataSource", "Snapshot received: ${snapshot.state}, lastEventId=${snapshot.lastEventId}, stateVersion=${snapshot.stateVersion}")

                _state.value = snapshot.state.toInternal()
                lastEventId.set(snapshot.lastEventId)
                lastSnapshotRefreshTime = System.currentTimeMillis()

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
                    val resp: SyncResponse = client.get(syncUrl(afterId)).body()

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
                                    val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                                    _state.value = snapshot.state.toInternal()
                                    lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }

                                "BetsConfirmed" -> {
                                    val obj = Json.parseToJsonElement(event.payloadJson).jsonObject
                                    val eventTableId = obj["tableId"]?.jsonPrimitive?.int ?: 0
                                    val boxIds = obj["boxIds"]?.jsonArray
                                        ?.mapNotNull { it.jsonPrimitive.intOrNull }
                                        ?.toSet()
                                        ?: emptySet()

                                    _events.tryEmit(
                                        DemoEvent.BetsConfirmed(
                                            tableId = eventTableId,
                                            boxIds = boxIds
                                        )
                                    )

                                    val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                                    _state.value = snapshot.state.toInternal()
                                    lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
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

                                    val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                                    _state.value = snapshot.state.toInternal()
                                    lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
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

                                    val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                                    _state.value = snapshot.state.toInternal()
                                    lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }

                                "PendingWinReset" -> {
                                    _events.tryEmit(DemoEvent.PendingWinReset)

                                    val snapshot: SnapshotResponse = client.get(snapshotUrl()).body()
                                    _state.value = snapshot.state.toInternal()
                                    lastEventId.set(maxOf(lastEventId.get(), snapshot.lastEventId))
                                    lastSuccessfulSyncTime = System.currentTimeMillis()
                                    _isHostOnline.value = true
                                }

                                else -> {
                                    Log.d("TableDataSource", "Ignoring unknown event type: ${event.type}")
                                }
                            }
                        }
                    } else {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastSnapshotRefreshTime >= 1500L) {
                            refreshSnapshot()
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
            val req = ToggleRequest(
                tableId = apiTableId(tableId),
                boxId = apiBoxId(boxId)
            )
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
            val req = ConfirmRequest(tableId = apiTableId(tableId))
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
        if (this.tableId != tableId) return

        runCatching {
            client.post("$baseUrl/input/payout/selectBox") {
                contentType(ContentType.Application.Json)
                setBody(
                    SelectPayoutBoxRequest(
                        tableId = apiTableId(tableId),
                        boxId = apiBoxId(boxId)
                    )
                )
            }
        }.onFailure {
            Log.e("TableDataSource", "Failed to select payout box", it)
        }
    }

    override suspend fun confirmPayout(tableId: Int) {
        if (_connectionState.value == ConnectionState.OFFLINE) return
        if (this.tableId != tableId) return

        runCatching {
            client.post("$baseUrl/input/payout/confirm") {
                contentType(ContentType.Application.Json)
                setBody(ConfirmPayoutRequest(tableId = apiTableId(tableId)))
            }
        }.onFailure {
            Log.e("TableDataSource", "Failed to confirm payout", it)
        }
    }

    @Serializable
    data class SnapshotResponse(
        val state: DemoState,
        val stateVersion: Long = 0,
        val lastEventId: Long = 0
    )

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