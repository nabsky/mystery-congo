package com.zorindisplays.display.emulator

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class DemoState(
    val jackpot1: Long = 10_000_000,
    val jackpot2: Long = 500_000,
    val jackpot3: Long = 100_000,

    // активные столы (keep-alive)
    val activeTables: Set<Int> = emptySet(),

    // preview ставки: table -> boxes (может быть несколько столов одновременно)
    val litBets: Map<Int, Set<Int>> = emptyMap(),
)

class Emulator(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val tableCount: Int = 8,
    private val boxesPerTable: Int = 9,
) {
    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state.asStateFlow()

    private val rnd = Random.Default

    // “реализм”: обычно почти все активны, иногда 1 стол может быть офф надолго
    private var stableInactive: Set<Int> = emptySet()

    // ограничим параллельные сценарии ставок
    private val inFlight = mutableSetOf<Int>()
    private val maxConcurrentBets = 3

    fun start() {
        scope.launch { activeTablesLoop() }
        scope.launch { betsSpawnerLoop() }
    }

    fun stop() {
        scope.coroutineContext.cancel()
    }

    // ---------- network-like inputs (future) ----------

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

    fun onResult(win: Boolean) {
        if (win) return
        val s = _state.value
        _state.value = s.copy(
            jackpot1 = s.jackpot1 + 1000,
            jackpot2 = s.jackpot2 + 1000,
            jackpot3 = s.jackpot3 + 1000,
        )
    }

    // ---------- loops ----------

    private suspend fun activeTablesLoop() {
        // старт: иногда 0 или 1 неактивный
        stableInactive = pickStableInactive()

        while (scope.isActive) {
            // редко меняем “неактивные” (например раз в ~60–90 сек)
            if (rnd.nextFloat() < 0.02f) {
                stableInactive = pickStableInactive()
            }

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

            // выбираем стол, который не занят сценарием
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

        // держим preview (в этот период боксы горят)
        delay(1200L + rnd.nextLong(-180L, 260L))

        // 2) confirm (в этот момент вылетят монетки + confirm-flash в TableStage)
        onBetConfirmed(table)

        // 3) result
        delay(260L + rnd.nextLong(-60L, 80L))
        val win = rnd.nextFloat() < 0.05f
        onResult(win = win)

        // пауза перед тем как этот стол снова может слать ставку
        delay(900L + rnd.nextLong(-220L, 320L))
    }

    private fun pickStableInactive(): Set<Int> {
        val roll = rnd.nextFloat()
        return when {
            roll < 0.70f -> emptySet()                 // чаще все активны
            roll < 0.95f -> setOf(rnd.nextInt(1, 9))   // иногда один офф
            else -> setOf(rnd.nextInt(1, 9), rnd.nextInt(1, 9)).distinct().toSet()
        }
    }
}

private fun Random.nextLong(min: Long, max: Long): Long =
    min + nextLong((max - min).coerceAtLeast(1))