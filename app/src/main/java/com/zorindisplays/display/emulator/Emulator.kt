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
    val jackpot1: Long = START_J1,
    val jackpot2: Long = START_J2,
    val jackpot3: Long = START_J3,

    val activeTables: Set<Int> = emptySet(),

    // preview ставки: table -> boxes (может быть несколько столов одновременно)
    val litBets: Map<Int, Set<Int>> = emptyMap(),
)

sealed interface DemoEvent {
    data class JackpotWin(
        val level: Int,      // 1..3
        val table: Int,      // 1..8
        val box: Int,        // 1..9
        val amountWon: Long  // snapshot суммы на момент выигрыша
    ) : DemoEvent

    // Dealer подтверждает выплату: сначала выбирает номер бокса (делаем кружок полым)
    data class DealerPayoutBoxSelected(
        val table: Int,
        val box: Int,
    ) : DemoEvent

    // Dealer жмёт Enter/Confirm: закрываем takeover
    data class DealerPayoutConfirmed(
        val table: Int,
        val box: Int,
    ) : DemoEvent
}

private const val START_J1: Long = 10_000_000
private const val START_J2: Long = 500_000
private const val START_J3: Long = 100_000

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

    // “реализм”: обычно почти все активны, иногда 1 стол может быть офф надолго
    private var stableInactive: Set<Int> = emptySet()

    // параллельные ставки
    private val inFlight = mutableSetOf<Int>()
    private val maxConcurrentBets = 3

    // пауза на время win-сцены
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
            // можно сразу убрать preview, чтобы не мешало
            val s = _state.value
            if (s.litBets.isNotEmpty()) _state.value = s.copy(litBets = emptyMap())
        }
    }

    fun resetJackpot(level: Int) {
        val s = _state.value
        _state.value = when (level) {
            1 -> s.copy(jackpot1 = START_J1)
            2 -> s.copy(jackpot2 = START_J2)
            else -> s.copy(jackpot3 = START_J3)
        }
    }

    // ---------- “network-like” methods (future) ----------

    fun onKeepAlive(active: Set<Int>) {
        _state.value = _state.value.copy(activeTables = active)
    }

    fun onBetPreview(table: Int, boxes: Set<Int>) {
        if (paused) return
        if (table !in 1..tableCount) return
        val clean = boxes.filter { it in 1..boxesPerTable }.toSet()

        val s = _state.value
        val m = s.litBets.toMutableMap()
        m[table] = clean
        _state.value = s.copy(litBets = m)
    }

    fun onBetConfirmed(table: Int) {
        if (paused) return
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

    private suspend fun emitWin(level: Int, table: Int, box: Int, amountWon: Long) {
        _events.emit(DemoEvent.JackpotWin(level, table, box, amountWon))

        // Эмуляция дилера:
        // 1) через 10с выбирает бокс (делаем его полым),
        // 2) ещё через 1с жмёт Enter/Confirm (закрываем takeover).
        scope.launch {
            delay(10_000L)
            if (paused) {
                _events.emit(DemoEvent.DealerPayoutBoxSelected(table = table, box = box))
                delay(1_000L)
                if (paused) {
                    _events.emit(DemoEvent.DealerPayoutConfirmed(table = table, box = box))
                }
            }
        }
    }

    // ---------- loops ----------

    private suspend fun activeTablesLoop() {
        stableInactive = pickStableInactive()

        while (scope.isActive) {
            // редко меняем “неактивные” (долго не переключаются)
            if (rnd.nextFloat() < 0.015f) {
                stableInactive = pickStableInactive()
            }

            val active = (1..tableCount).filterNot { stableInactive.contains(it) }.toSet()
            onKeepAlive(active)

            delay(1000L + rnd.nextLong(-140L, 220L))
        }
    }

    private suspend fun betsSpawnerLoop() {
        while (scope.isActive) {
            delay(650L + rnd.nextLong(-160L, 260L))
            if (paused) continue

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
        if (paused) return

        // 1) preview (1..2 бокса)
        val count = if (rnd.nextBoolean()) 1 else 2
        val boxes = mutableSetOf<Int>()
        while (boxes.size < count) boxes += rnd.nextInt(1, boxesPerTable + 1)
        onBetPreview(table, boxes)

        delay(1200L + rnd.nextLong(-220L, 320L))
        if (paused) return

        // 2) confirm
        onBetConfirmed(table)

        delay(260L + rnd.nextLong(-70L, 90L))
        if (paused) return

        // 3) result
        val win = rnd.nextFloat() < 0.055f
        if (win) {
            // чаще мелкий, реже большой
            val roll = rnd.nextFloat()
            val level = when {
                roll < 0.07f -> 1
                roll < 0.37f -> 2
                else -> 3
            }
            val winnerBox = boxes.random(rnd)
            val amountWon = when (level) {
                1 -> _state.value.jackpot1
                2 -> _state.value.jackpot2
                else -> _state.value.jackpot3
            }
            emitWin(level = level, table = table, box = winnerBox, amountWon = amountWon)
        } else {
            onNoWinBump()
        }

        delay(900L + rnd.nextLong(-240L, 380L))
    }

    private fun pickStableInactive(): Set<Int> {
        val roll = rnd.nextFloat()
        return when {
            roll < 0.78f -> emptySet()                 // чаще все активны
            roll < 0.96f -> setOf(rnd.nextInt(1, 9))   // иногда один офф
            else -> setOf(rnd.nextInt(1, 9), rnd.nextInt(1, 9)).distinct().toSet()
        }
    }
}

private fun Random.nextLong(min: Long, max: Long): Long =
    min + nextLong((max - min).coerceAtLeast(1))