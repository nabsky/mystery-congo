package com.zorindisplays.display.emulator

import com.zorindisplays.display.model.DemoState
import com.zorindisplays.display.model.DemoEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

private const val START_RUBY: Long = 10_000_000
private const val START_GOLD: Long = 500_000
private const val START_JADE: Long = 100_000

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
    @Volatile private var paused: Boolean = false

    fun start() {
        scope.launch { activeTablesLoop() }
        scope.launch { betsSpawnerLoop() }
    }

    fun stop() {
        scope.coroutineContext.cancel()
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (value) {
            // Очищаем только lit bets, jackpots не трогаем
            rebuildState(_state.value.jackpots, clearLitBets = true)
        }
    }

    fun resetJackpot(level: Int) {
        val jackpots = _state.value.jackpots.toMutableMap()
        when (level) {
            1 -> jackpots["RUBY"] = START_RUBY
            2 -> jackpots["GOLD"] = START_GOLD
            else -> jackpots["JADE"] = START_JADE
        }
        rebuildState(jackpots)
    }

    fun onKeepAlive(active: Set<Int>) {
        rebuildState(_state.value.jackpots, false, activeTables = active)
    }

    fun onBetPreview(table: Int, boxes: Set<Int>) {
        if (paused) return
        if (table !in 0 until tableCount) return
        val clean = boxes.filter { it in 0 until boxesPerTable }.toSet()
        val litBets = _state.value.tables.associate { it.tableId to it.activeBoxes }.toMutableMap()
        litBets[table] = clean
        rebuildState(_state.value.jackpots, false, litBets = litBets)
    }

    fun onBetConfirmed(table: Int) {
        if (paused) return
        if (table !in 0 until tableCount) return

        val currentLitBets = _state.value.tables.associate { it.tableId to it.activeBoxes }
        val confirmedBoxes = currentLitBets[table].orEmpty()
        if (confirmedBoxes.isEmpty()) return

        val litBets = currentLitBets.toMutableMap()
        litBets.remove(table)

        rebuildState(_state.value.jackpots, false, litBets = litBets)

        _events.tryEmit(
            DemoEvent.BetsConfirmed(
                tableId = table,
                boxIds = confirmedBoxes
            )
        )
    }

    private fun onNoWinBump() {
        val jackpots = _state.value.jackpots.toMutableMap()
        jackpots["RUBY"] = (jackpots["RUBY"] ?: START_RUBY) + 1000
        jackpots["GOLD"] = (jackpots["GOLD"] ?: START_GOLD) + 1000
        jackpots["JADE"] = (jackpots["JADE"] ?: START_JADE) + 1000
        rebuildState(jackpots)
    }

    private suspend fun emitWin(level: Int, table: Int, box: Int, amountWon: Long) {
        val jackpotId = when (level) {
            1 -> "RUBY"
            2 -> "GOLD"
            else -> "JADE"
        }
        _events.emit(DemoEvent.JackpotHit(jackpotId, table, box, amountWon))
        scope.launch {
            delay(10_000L)
            if (paused) {
                _events.emit(DemoEvent.DealerPayoutBoxSelected(tableId = table, boxId = box))
                delay(1_000L)
                if (paused) {
                    _events.emit(DemoEvent.DealerPayoutConfirmed(tableId = table, boxId = box))
                }
            }
        }
    }

    private suspend fun activeTablesLoop() {
        stableInactive = pickStableInactive()
        while (scope.isActive) {
            if (rnd.nextFloat() < 0.015f) {
                stableInactive = pickStableInactive()
            }
            val active = (0 until tableCount).filterNot { stableInactive.contains(it) }.toSet()
            onKeepAlive(active)
            delay(1000L + rnd.nextLong(-140L, 220L))
        }
    }

    private suspend fun betsSpawnerLoop() {
        while (scope.isActive) {
            delay(650L + rnd.nextLong(-160L, 260L))
            if (paused) continue
            val active = _state.value.tables.filter { it.isActive }.map { it.tableId }
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
        if (paused) return

        val count = if (rnd.nextBoolean()) 1 else 2
        val boxes = mutableSetOf<Int>()
        while (boxes.size < count) boxes += rnd.nextInt(0, boxesPerTable)

        onBetPreview(table, boxes)

        // игрок "держит" ставки перед confirm
        delay(1200L + rnd.nextLong(-220L, 320L))
        if (paused) return

        onBetConfirmed(table)

        // ДАЁМ confirm-анимации время:
        // вспышка + вылет монет + чуть-чуть на визуальное восприятие
        delay(900L + rnd.nextLong(-120L, 180L))
        if (paused) return

        val win = rnd.nextFloat() < 0.055f
        if (win) {
            val roll = rnd.nextFloat()
            val level = when {
                roll < 0.07f -> 1
                roll < 0.37f -> 2
                else -> 3
            }

            val winnerBox = boxes.random(rnd)
            val amountWon = when (level) {
                1 -> _state.value.jackpots["RUBY"] ?: START_RUBY
                2 -> _state.value.jackpots["GOLD"] ?: START_GOLD
                else -> _state.value.jackpots["JADE"] ?: START_JADE
            }

            emitWin(
                level = level,
                table = table,
                box = winnerBox,
                amountWon = amountWon
            )
        } else {
            onNoWinBump()
        }

        delay(900L + rnd.nextLong(-240L, 380L))
    }

    private fun pickStableInactive(): Set<Int> {
        val roll = rnd.nextFloat()
        return when {
            roll < 0.78f -> emptySet()
            roll < 0.96f -> setOf(rnd.nextInt(0, tableCount))
            else -> setOf(rnd.nextInt(0, tableCount), rnd.nextInt(0, tableCount)).distinct().toSet()
        }
    }

    private fun rebuildState(
        jackpots: Map<String, Long> = _state.value.jackpots,
        clearLitBets: Boolean = false,
        activeTables: Set<Int>? = null,
        litBets: Map<Int, Set<Int>>? = null
    ) {
        val jps = if (jackpots.isEmpty()) _state.value.jackpots else jackpots
        val actTables = activeTables ?: _state.value.tables.filter { it.isActive }.map { it.tableId }.toSet()
        val lit = if (clearLitBets) emptyMap() else litBets ?: _state.value.tables.associate { it.tableId to it.activeBoxes }
        val tables = (0 until tableCount).map { idx ->
            DemoState.Table(
                tableId = idx,
                activeBoxes = lit[idx] ?: emptySet(),
                recentBoxes = emptySet(),
                isActive = actTables.contains(idx)
            )
        }
        _state.value = DemoState(
            tables = tables,
            jackpots = jps
        )
    }
}

private fun Random.nextLong(min: Long, max: Long): Long =
    min + nextLong((max - min).coerceAtLeast(1))