package com.zorindisplays.display.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
data class WinnerFocus(
    val table: Int,
    val box: Int,
    val color: Color
)

@Composable
fun TableStage(
    states: TableStatesLike,
    // preview подсветки (могут быть у нескольких столов одновременно)
    litBets: Map<Int, Set<Int>>,
    modifier: Modifier = Modifier,
    tableCount: Int = 8,
    tableHeight: Dp,
    labelTextStyle: TextStyle,

    // склейка
    confirmFlashMs: Long = 110L,
    winner: WinnerFocus? = null,
    onCentersReady: ((originInRoot: Offset, centers: Map<Pair<Int,Int>, Offset>) -> Unit)? = null
) {
    var tableOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var centersLocal by remember { mutableStateOf<Map<Pair<Int, Int>, Offset>>(emptyMap()) }

    var rootSize by remember { mutableStateOf(Offset(0f, 0f)) }
    var burst by remember { mutableStateOf<CoinBurst?>(null) }

    // цель: улёт за верх экрана
    val targetInRoot = remember(rootSize) {
        val w = rootSize.x
        val h = rootSize.y
        Offset(w * 0.52f, -h * 0.12f)
    }

    // last preview map
    var lastLitBets by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }

    // confirm-flash overlay
    var flashBets by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    var flashToken by remember { mutableStateOf(0L) }

    // wrapper states: подсветка = preview + flash
    val mergedStates = remember(states, litBets, flashBets) {
        object : TableStatesLike {
            override fun isActive(table: Int): Boolean = states.isActive(table)
            override fun hasBetOnBox(table: Int, box: Int): Boolean {
                val a = litBets[table]?.contains(box) == true
                if (a) return true
                return flashBets[table]?.contains(box) == true
            }
        }
    }

    /**
     * Триггер на CONFIRM:
     * если у какого-то стола была подсветка, а теперь её нет -> confirm.
     * На confirm:
     * 1) burst монет из старых боксов
     * 2) flash этих боксов на confirmFlashMs
     */
    LaunchedEffect(litBets, centersLocal, tableOriginInRoot, targetInRoot) {
        val old = lastLitBets
        val now = litBets

        if (centersLocal.isNotEmpty() && old.isNotEmpty()) {
            // какие столы “подтвердились”
            val confirmedTables = old.keys.filter { t ->
                val was = old[t].orEmpty()
                val isNow = now[t].orEmpty()
                was.isNotEmpty() && isNow.isEmpty()
            }

            if (confirmedTables.isNotEmpty()) {
                // 1) монеты: собираем все sources одним burst
                val sources = buildList {
                    for (t in confirmedTables) {
                        for (b in old[t].orEmpty()) {
                            val c = centersLocal[(t to b)] ?: continue
                            add(Offset(tableOriginInRoot.x + c.x, tableOriginInRoot.y + c.y))
                        }
                    }
                }

                if (sources.isNotEmpty()) {
                    burst = CoinBurst(
                        sourcesInRoot = sources,
                        targetInRoot = targetInRoot
                    )
                }

                // 2) flash: держим старые боксы ещё чуть-чуть
                val token = System.nanoTime()
                flashToken = token
                flashBets = confirmedTables.associateWith { old[it].orEmpty() }

                // авто-гашение флэша
                launch {
                    delay(confirmFlashMs)
                    if (flashToken == token) flashBets = emptyMap()
                }
            }
        }

        lastLitBets = litBets
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val s = coords.size
                rootSize = Offset(s.width.toFloat(), s.height.toFloat())
            }
    ) {
        CoinsLayer(
            burst = burst,
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        )

        TableRowView(
            states = mergedStates,
            tableCount = tableCount,
            height = tableHeight,
            labelTextStyle = labelTextStyle,
            modifier = androidx.compose.ui.Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coords ->
                    tableOriginInRoot = coords.positionInRoot()
                },
            onBoxCenters = { centersLocal = it },
            betFillOverride = { t, b ->
                if (winner != null && winner.table == t && winner.box == b) winner.color else null
            }
        )
    }
}