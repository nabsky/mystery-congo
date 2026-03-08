package com.zorindisplays.mystery.host

import com.zorindisplays.mystery.model.JackpotDataSource
import com.zorindisplays.mystery.model.JackpotState
import com.zorindisplays.mystery.model.JackpotEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlinx.serialization.json.JsonPrimitive


class HostDataSource(
    private val repository: HostRepository,
    private val scope: CoroutineScope,
    private val tableCount: Int = 8,
    private val boxCount: Int = 9,
    private val stakePerBox: Long = 100
) : JackpotDataSource {
    var onClose: (() -> Unit)? = null

    private val _state = MutableStateFlow(JackpotState())
    override val state: StateFlow<JackpotState> get() = _state
    private val _events = MutableSharedFlow<JackpotEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<JackpotEvent> get() = _events

    private var systemMode: JackpotState.SystemMode = JackpotState.SystemMode.ACCEPTING_BETS
    private var pendingWin: PendingWin? = null
    private val contributionPerBox = mapOf("RUBY" to 200000L, "GOLD" to 100000L, "JADE" to 50000L)
    private val odds = mapOf("RUBY" to 100000, "GOLD" to 20000, "JADE" to 5000)
    private val jackpotInitials = mapOf("RUBY" to 100000L, "GOLD" to 20000L, "JADE" to 5000L)
    private var jackpots: MutableMap<String, Long> = jackpotInitials.toMutableMap()
    private val lastActivity = LongArray(tableCount) { System.currentTimeMillis() }
    private val activeBoxes = Array(tableCount) { mutableSetOf<Int>() }
    private val recentBoxes = Array(tableCount) { mutableMapOf<Int, Long>() }
    private var job: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            repository.ensureInitialized()
            jackpots.putAll(repository.getJackpotState())
            updateState()
            startRecentFade()
        }
    }

    private fun updateState() {
        val now = System.currentTimeMillis()
        val tables = (0 until tableCount).map { tableId ->
            val active = activeBoxes[tableId].toSet()
            val recent = recentBoxes[tableId].filter { now - it.value < RECENT_FADE_MS }.keys.toSet()
            val isActive = now - lastActivity[tableId] < TABLE_ACTIVE_TIMEOUT_MS
            JackpotState.Table(
                tableId = tableId,
                activeBoxes = active,
                recentBoxes = recent,
                isActive = isActive
            )
        }
        _state.value = JackpotState(
            tables = tables,
            jackpots = jackpots.toMap(),
            systemMode = systemMode,
            pendingWin = pendingWin?.toDemoPendingWin()
        )
    }

    private fun startRecentFade() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(1000)
                updateState()
            }
        }
    }

    override fun start(scope: CoroutineScope) { /* no-op, уже запущено */ }
    override suspend fun stop() {
        job?.cancel()
        onClose?.invoke()
    }

    override suspend fun toggleBox(tableId: Int, boxId: Int) {
        if (systemMode != JackpotState.SystemMode.ACCEPTING_BETS) return
        lastActivity[tableId] = System.currentTimeMillis()
        if (activeBoxes[tableId].contains(boxId)) {
            activeBoxes[tableId].remove(boxId)
        } else {
            // если это первый toggle после confirm, сбрасываем RECENT
            if (activeBoxes[tableId].isEmpty()) recentBoxes[tableId].clear()
            activeBoxes[tableId].add(boxId)
        }
        updateState()
        val payloadJson = buildJsonObject {
            put("tableId", tableId)
            put("boxId", boxId)
        }.toString()
        repository.applyEvent("BoxToggled", payloadJson)
    }

    override suspend fun confirmBets(tableId: Int) {
        if (systemMode != JackpotState.SystemMode.ACCEPTING_BETS) return
        val boxIds = activeBoxes[tableId].toList().sorted()
        if (boxIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val bumpTotals = mutableMapOf<String, Long>()
        // bump джекпоты
        for ((jackpotId, contrib) in contributionPerBox) {
            bumpTotals[jackpotId] = contrib * boxIds.size
            jackpots[jackpotId] = (jackpots[jackpotId] ?: 0) + bumpTotals[jackpotId]!!
        }
        val payloadJson = buildJsonObject {
            put("tableId", JsonPrimitive(tableId))
            putJsonArray("boxIds") { boxIds.forEach { add(JsonPrimitive(it)) } }
            put("stakePerBox", JsonPrimitive(stakePerBox))
            putJsonObject("bumpTotals") { bumpTotals.forEach { (j, v) -> put(j, JsonPrimitive(v)) } }
        }.toString()
        repository.applyEvent(
            "BetsConfirmed",
            payloadJson,
            jackpots.toMap()
        )
        // RNG
        var win: PendingWin? = null
        for (boxId in boxIds) {
            for (jackpotId in listOf("RUBY", "GOLD", "JADE")) {
                val n = odds[jackpotId] ?: continue
                if (win == null && Random.nextInt(n) == 0) {
                    win = PendingWin(jackpotId, tableId, boxId, jackpots[jackpotId] ?: 0)
                    systemMode = JackpotState.SystemMode.PAYOUT_PENDING
                    pendingWin = win
                    _events.tryEmit(JackpotEvent.JackpotHit(jackpotId, tableId, boxId, win.winAmount))
                    val hitPayload = buildJsonObject {
                        put("jackpotId", JsonPrimitive(jackpotId))
                        put("tableId", JsonPrimitive(tableId))
                        put("boxId", JsonPrimitive(boxId))
                        put("winAmount", JsonPrimitive(win.winAmount))
                        putJsonObject("context") {
                            putJsonArray("boxIds") { boxIds.forEach { add(JsonPrimitive(it)) } }
                            put("stakePerBox", JsonPrimitive(stakePerBox))
                        }
                    }.toString()
                    repository.applyEvent("JackpotHitDetected", hitPayload)
                    break
                }
            }
            if (win != null) break
        }
        activeBoxes[tableId].forEach { recentBoxes[tableId][it] = now }
        activeBoxes[tableId].clear()
        updateState()
    }

    override suspend fun selectPayoutBox(tableId: Int, boxId: Int) {
        if (pendingWin?.tableId == tableId && pendingWin?.boxId != boxId) {
            pendingWin = pendingWin?.copy(boxId = boxId)
            updateState()
            val payloadJson = buildJsonObject {
                put("tableId", tableId)
                put("boxId", boxId)
            }.toString()
            repository.applyEvent("PayoutSelectedBox", payloadJson)
        }
    }

    override suspend fun confirmPayout(tableId: Int) {
        val win = pendingWin ?: return
        if (win.tableId != tableId) return
        val jackpotId = win.jackpotId
        val initialAmount = jackpotInitials[jackpotId] ?: 0L
        jackpots[jackpotId] = initialAmount
        val payoutPayload = buildJsonObject {
            put("jackpotId", jackpotId)
            put("tableId", tableId)
            put("boxId", win.boxId)
            put("winAmount", win.winAmount)
            put("resetTo", initialAmount)
        }.toString()
        repository.applyEvent(
            "PayoutConfirmed",
            payoutPayload,
            jackpots.toMap()
        )
        systemMode = JackpotState.SystemMode.ACCEPTING_BETS
        pendingWin = null
        updateState()
    }

    private fun PendingWin.toDemoPendingWin(): JackpotState.PendingWin =
        JackpotState.PendingWin(jackpotId, tableId, boxId, winAmount)

    companion object {
        private const val RECENT_FADE_MS = 60_000L
        private const val TABLE_ACTIVE_TIMEOUT_MS = 60_000L
    }

    private data class PendingWin(
        val jackpotId: String,
        val tableId: Int,
        val boxId: Int,
        val winAmount: Long
    )
}
