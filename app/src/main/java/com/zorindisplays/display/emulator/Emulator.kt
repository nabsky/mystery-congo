package com.zorindisplays.display.emulator

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class DemoState(
    val jackpot1: Long = 10_000_000,
    val jackpot2: Long = 500_000,
    val jackpot3: Long = 100_000,

    val activeTables: Set<Int> = emptySet(),
    val litBets: Map<Int, Set<Int>> = emptyMap(),
)

sealed interface DemoEvent {
    data class JackpotWin(val level: Int) : DemoEvent // 1..3
}

class Emulator(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val tableCount: Int = 8,
    private val boxesPerTable: Int = 9,
) {
    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DemoEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DemoEvent> = _events.asSharedFlow()

    private val rnd = Random.Default

    private var stableInactive: Set<Int> = emptySet()
    private val inFlight = mutableSetOf<Int>()
    private val maxConcurrentBets = 3

    fun start() {
        scope.launch { activeTablesLoop() }
        scope.launch { betsSpawnerLoop() }
    }

    fun stop() {
        scope.coroutineContext.cancel()
    }

    fun onKeepAlive(active: Set<Int>) {
        _state.value = _state.value.copy(activeTables = active)
    }

    fun onBetPreview(table: Int, boxes: Set<Int>) {
        if (table !in 1..tableCount) return
        val clean = boxes.filter { it in 1..boxesPerTable }.toSet()
        val s = _state.value
        val m = s.litBets.toMutableMap()
        m[table] = clean
        _state.value = s.copy(litBets = m)
    }

    fun onBetConfirmed(table: Int) {
        val s = _state.value
        if (!s.litBets.containsKey(table)) return
        val m = s.litBets.toMutableMap()
        m.remove(table)
        _state.value = s.copy(litBets = m)
    }

    private fun onNoWinBump() {
        val s = _state.value
        _state.value = s.copy(
            jackpot1 = s.jackpot1 + 1000,
            jackpot2 = s.jackpot2 + 1000,
            jackpot3 = s.jackpot3 + 1000,
        )
    }

    private suspend fun emitWin(level: Int) {
        _events.emit(DemoEvent.JackpotWin(level))
    }

    // ---------- loops ----------

    private suspend fun activeTablesLoop() {
        stableInactive = pickStableInactive()
        while (scope.isActive) {
            if (rnd.nextFloat() < 0.02f) stableInactive = pickStableInactive()
            val active = (1..tableCount).filterNot { stableInactive.contains(it) }.toSet()
            onKeepAlive(active)
            delay(900L + rnd.nextLong(-120L, 180L))
        }
    }

    private suspend fun betsSpawnerLoop() {
        while (scope.isActive) {
            delay(700L + rnd.nextLong(-160L, 220L))

            val active = _state.value.activeTables.toList()
            if (active.isEmpty()) continue
            if (inFlight.size >= maxConcurrentBets) continue

            val candidates = active.filterNot { inFlight.contains(it) }
            if (candidates.isEmpty()) continue
            val table = candidates.random(rnd)

            inFlight += table
            scope.launch {
                try {
                    runBetScenario(table)
                } finally {
                    inFlight -= table
                }
            }
        }
    }

    private suspend fun runBetScenario(table: Int) {
        // 1) preview
        val count = if (rnd.nextBoolean()) 1 else 2
        val boxes = mutableSetOf<Int>()
        while (boxes.size < count) boxes += rnd.nextInt(1, boxesPerTable + 1)
        onBetPreview(table, boxes)

        delay(1200L + rnd.nextLong(-180L, 260L))

        // 2) confirm
        onBetConfirmed(table)

        delay(260L + rnd.nextLong(-60L, 80L))

        // 3) result
        val win = rnd.nextFloat() < 0.055f
        if (win) {
            // какой джекпот выпал (чаще мелкий, реже большой)
            val level = when {
                rnd.nextFloat() < 0.08f -> 1
                rnd.nextFloat() < 0.38f -> 2
                else -> 3
            }
            emitWin(level)
        } else {
            onNoWinBump()
        }

        delay(900L + rnd.nextLong(-220L, 320L))
    }

    private fun pickStableInactive(): Set<Int> {
        val roll = rnd.nextFloat()
        return when {
            roll < 0.70f -> emptySet()
            roll < 0.95f -> setOf(rnd.nextInt(1, 9))
            else -> setOf(rnd.nextInt(1, 9), rnd.nextInt(1, 9)).distinct().toSet()
        }
    }
}

private fun Random.nextLong(min: Long, max: Long): Long =
    min + nextLong((max - min).coerceAtLeast(1))