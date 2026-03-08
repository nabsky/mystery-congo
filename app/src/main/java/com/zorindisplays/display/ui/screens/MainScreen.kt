package com.zorindisplays.display.ui.screens

import JackpotGemsOverlay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import com.nabsky.mystery.component.TableViewColors
import com.zorindisplays.display.R
import com.zorindisplays.display.model.DemoEvent
import com.zorindisplays.display.model.DemoState
import com.zorindisplays.display.ui.components.*
import com.zorindisplays.display.ui.theme.ChangoRegular
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable

private sealed interface WinPhase {
    data object None : WinPhase

    sealed interface WithWinData : WinPhase {
        val level: Int
        val table: Int
        val box: Int
        val amountWon: Long
    }

    data class Rain(
        override val level: Int,
        override val table: Int,
        override val box: Int,
        override val amountWon: Long,
    ) : WithWinData

    data class Focus(
        override val level: Int,
        override val table: Int,
        override val box: Int,
        override val amountWon: Long,
    ) : WithWinData

    data class Takeover(
        override val level: Int,
        override val table: Int,
        override val box: Int,
        override val amountWon: Long,
    ) : WithWinData
}

@Composable
fun MainScreen(
    viewModel: com.zorindisplays.display.model.MainViewModel,
    tableId: Int = 0,
    onResetRole: () -> Unit
) {
    val dataSource = viewModel.dataSource
    val demo: DemoState by dataSource.state.collectAsState()

    val jackpot1 = demo.jackpots["RUBY"] ?: 0L
    val jackpot2 = demo.jackpots["GOLD"] ?: 0L
    val jackpot3 = demo.jackpots["JADE"] ?: 0L

    val activeTables: Set<Int> = demo.tables.filter { it.isActive }.map { it.tableId }.toSet()
    val litBets: Map<Int, Set<Int>> = demo.tables.associate { it.tableId to it.activeBoxes }

    // Старт/остановка эмулятора
    val uiScope = rememberCoroutineScope()

    // Focus handling for keyboard input
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(dataSource) {
        dataSource.start(uiScope)
        onDispose { uiScope.launch { dataSource.stop() } }
    }
    val emu = dataSource as? com.zorindisplays.display.model.EmulatorDataSource

    var win by remember { mutableStateOf<WinPhase>(WinPhase.None) }
    var payoutSelectedBox by remember { mutableStateOf<Pair<Int, Int>?>(null) } // table to box

    // burst монет из центра джекпотов -> в выигравший бокс
    var winBurst by remember { mutableStateOf<CoinBurst?>(null) }
    var rain by remember { mutableStateOf<RainRequest?>(null) }
    var confirmedBurst by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    var confirmToken by remember { mutableLongStateOf(0L) }

    // интрига: winner box подсвечиваем только ПОСЛЕ того, как монетки "втекли" в него
    var revealWinnerBox by remember { mutableStateOf(false) }

    fun levelFromJackpotId(id: String): Int = when (id) {
        "RUBY" -> 1
        "GOLD" -> 2
        "JADE" -> 3
        else -> 1
    }

    fun apiTableToUi0(apiTableId: Int): Int = (apiTableId - 1).coerceIn(0, 7)
    fun apiBoxToUi0(apiBoxId: Int): Int = (apiBoxId - 1).coerceIn(0, 8)

    fun uiTable0(raw: Int): Int = raw.coerceIn(0, 7)
    fun uiBox0(raw: Int): Int = raw.coerceIn(0, 8)

    var winJackpotAmountMinor by remember { mutableStateOf<Long?>(null) }
    var winJackpotLevel by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) { // KeyDown feels more responsive for games
                    val boxIdx = when (event.key) {
                        Key.One, Key.NumPad1 -> 0
                        Key.Two, Key.NumPad2 -> 1
                        Key.Three, Key.NumPad3 -> 2
                        Key.Four, Key.NumPad4 -> 3
                        Key.Five, Key.NumPad5 -> 4
                        Key.Six, Key.NumPad6 -> 5
                        Key.Seven, Key.NumPad7 -> 6
                        Key.Eight, Key.NumPad8 -> 7
                        Key.Nine, Key.NumPad9 -> 8
                        else -> -1
                    }

                    // Check for Jackpot Payout Mode (Takeover)
                    val currentWin = win
                    if (currentWin is WinPhase.Takeover && currentWin.table == tableId) {
                        if (boxIdx >= 0) {
                             uiScope.launch { dataSource.selectPayoutBox(tableId, boxIdx) }
                             return@onKeyEvent true
                        }
                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                             uiScope.launch { dataSource.confirmPayout(tableId) }
                             return@onKeyEvent true
                        }
                        // Ignore other keys during takeover for this table? Or allow pass-through?
                        // Better to consume if it's a number/enter to avoid accidental bets if logic allows (though bets should be blocked by system status)
                        return@onKeyEvent false
                    }

                    // Normal Betting Mode
                    if (boxIdx >= 0) {
                        uiScope.launch { dataSource.toggleBox(tableId, boxIdx) }
                        return@onKeyEvent true
                    }
                    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        uiScope.launch { dataSource.confirmBets(tableId) }
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        val scope = this

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        val density = LocalDensity.current
        val wPx = with(density) { scope.maxWidth.toPx() }
        val hPx = with(density) { scope.maxHeight.toPx() }

        val tableHeight = 240.dp
        val tableHeightPx = with(density) { tableHeight.toPx() }

        val labelFontPx = with(density) { 32.sp.toPx() } // как у labelTextStyle
        val hpPx = with(density) { 16.dp.toPx() }
        val bpPx = with(density) { 14.dp.toPx() }
        val labelGapPx = with(density) { 10.dp.toPx() }

        // (2) центр зоны джекпотов (для позиционирования цифр/фокуса)
        val center2 = Offset(wPx * 0.5f, hPx * 0.38f)
        // цель дождя делаем отдельно и чуть выше
        val rainTarget = Offset(center2.x, hPx * 0.33f)

        val labelStyle = TextStyle(
            color = Color.White,
            fontSize = 32.sp,
            fontFamily = MontserratBold,
        )

        // слушаем win events
        LaunchedEffect(dataSource) {
            dataSource.events.collectLatest { e ->
                when (e) {
                    is DemoEvent.JackpotHit -> {
                        val level = levelFromJackpotId(e.jackpotId)
                        val table = apiTableToUi0(e.tableId)
                        val box = apiBoxToUi0(e.boxId)
                        winJackpotLevel = level
                        winJackpotAmountMinor = e.winAmount
                        payoutSelectedBox = null
                        emu?.emulator?.setPaused(true)
                        revealWinnerBox = false
                        win = WinPhase.Rain(level, table, box, e.winAmount)
                        rain = RainRequest(target = rainTarget)
                        delay(1500)
                        rain = null
                        win = WinPhase.Focus(level, table, box, e.winAmount)
                        delay(16)

                        val winnerCenterInRoot = computeBoxCenterInRootPx(
                            table = table,
                            box = box,
                            rootW = wPx,
                            rootH = hPx,
                            tableHeightPx = tableHeightPx,
                            horizontalPaddingPx = hpPx,
                            bottomPaddingPx = bpPx,
                            labelGapAboveTablePx = labelGapPx,
                            labelFontSizePx = labelFontPx,
                            spacingToRadius = 0.30f
                        )

                        val coinBurstDurationMs = 1100L // соответствует CoinsLayer.durationMs по умолчанию
                        winBurst = CoinBurst(
                            sourcesInRoot = listOf(rainTarget),
                            targetInRoot = winnerCenterInRoot
                        )

                        // ждём, пока основная масса монет долетит
                        delay(coinBurstDurationMs + 220L)
                        winBurst = null

                        // (4) Reveal winner box: теперь подсвечиваем, даём зрителю осознать
                        revealWinnerBox = true
                        delay(850)

                        // (5) Takeover
                        win = WinPhase.Takeover(level, table, box, e.winAmount)
                        // Дальше ждём подтверждения дилера через события (см. ниже)
                    }

                    is DemoEvent.DealerPayoutBoxSelected -> {
                        val cur = win
                        val tId = apiTableToUi0(e.tableId)
                        val bId = apiBoxToUi0(e.boxId)
                        if (cur is WinPhase.Takeover && cur.table == tId) {
                            payoutSelectedBox = tId to bId
                        }
                    }

                    is DemoEvent.DealerPayoutConfirmed -> {
                        val cur = win
                        val tId = apiTableToUi0(e.tableId)
                        if (cur is WinPhase.Takeover && cur.table == tId) {
                            emu?.emulator?.resetJackpot(cur.level)
                            payoutSelectedBox = null
                            win = WinPhase.None
                            revealWinnerBox = false
                            winJackpotLevel = null
                            winJackpotAmountMinor = null
                            emu?.emulator?.setPaused(false)
                        }
                    }

                    is DemoEvent.BetsConfirmed -> {
                        val tId = apiTableToUi0(e.tableId)
                        val boxes = e.boxIds.map { apiBoxToUi0(it) }.toSet()

                        confirmedBurst = mapOf(tId to boxes)
                        confirmToken = System.nanoTime()
                    }
                }
            }
        }

        LaunchedEffect(demo.systemMode, demo.pendingWin) {
            val pending = demo.pendingWin
            if (demo.systemMode == DemoState.SystemMode.PAYOUT_PENDING && pending != null) {
                if (win == WinPhase.None) {
                    val level = levelFromJackpotId(pending.jackpotId)

                    winJackpotLevel = level
                    winJackpotAmountMinor = pending.winAmount
                    payoutSelectedBox = null
                    revealWinnerBox = true
                    emu?.emulator?.setPaused(true)

                    win = WinPhase.Takeover(
                        level = level,
                        table = pending.tableId,
                        box = pending.boxId,
                        amountWon = pending.winAmount
                    )
                }
            }
        }

        val h = maxHeight
        val overlayH = maxHeight
        val hPxLocal = hPx

        // ======== Base scene layers (не показываем, когда Takeover) ========
        val takeover = win is WinPhase.Takeover
        val focus = win is WinPhase.Focus
        // val raining = win is WinPhase.Rain // не используется

        if (!takeover) {
            LuxuryBackground(modifier = Modifier.fillMaxSize())

            // spotlight (можно усилить на focus)
            val spotAlpha = when (val w = win) {
                is WinPhase.Rain -> 0.22f
                is WinPhase.Focus -> when (w.level) {
                    1 -> 0.30f
                    2 -> 0.26f
                    else -> 0.24f
                }
                else -> 0.18f
            }
            JackpotSpotlight(
                modifier = Modifier
                    .fillMaxSize()
                    //.offset(y = h * 0.10f)
                    .blur(24.dp),
                baseColor = Color(0xFFFF2A2A),
                alpha = spotAlpha,
                winnerLevel = when (val w = win) {
                    is WinPhase.Rain -> w.level
                    is WinPhase.Focus -> w.level
                    is WinPhase.Takeover -> w.level
                    else -> null
                },
                winPhase = when (win) {
                    is WinPhase.Rain -> "Rain"
                    is WinPhase.Focus -> "Focus"
                    is WinPhase.Takeover -> "Takeover"
                    else -> "None"
                }
            )


            // Логотип Mystery Jackpot по центру сверху
            var clickCount by remember { mutableStateOf(0) }
            LaunchedEffect(clickCount) {
                if (clickCount > 0) {
                   delay(2000)
                   clickCount = 0
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp), // Можно скорректировать отступ
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    painter = painterResource(R.drawable.mystery_jackpot_logo_170x230),
                    contentDescription = "Mystery Jackpot Logo",
                    modifier = Modifier
                        .width(230.dp)
                        .height(170.dp)
                        .clickable {
                             clickCount++
                             if (clickCount >= 5) {
                                 onResetRole()
                                 clickCount = 0
                             }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            GemSpotlightsOverlay(
                modifier = Modifier.fillMaxSize(),
                winnerLevel = when (val w = win) {
                    is WinPhase.Rain -> w.level
                    is WinPhase.Focus -> w.level
                    is WinPhase.Takeover -> w.level
                    else -> null
                },
                winPhase = when (win) {
                    is WinPhase.Rain -> "Rain"
                    is WinPhase.Focus -> "Focus"
                    is WinPhase.Takeover -> "Takeover"
                    else -> "None"
                }
            )

            var winnerLevel = when (val w = win) {
                is WinPhase.Rain -> w.level
                is WinPhase.Focus -> w.level
                is WinPhase.Takeover -> w.level
                else -> null
            }

            val winnerColor = when (winnerLevel) {
                1 -> Color(0xFFFF2A2A)
                2 -> Color(0xFFFFE08A)
                3 -> Color(0xFF59E0A7)
                else -> null
            }
            val isRainPhase = win is WinPhase.Rain
            val isFocusPhase = win is WinPhase.Focus

            winnerColor?.let { color ->
                JackpotLightBeam(
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    visible = isRainPhase || isFocusPhase
                )

                JackpotRainAura(
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    visible = isRainPhase || isFocusPhase
                )
            }

            BreathingVignette(
                modifier = Modifier.fillMaxSize(),
                strength = 0.22f,
                periodMs = 9000
            )



            TopBrandBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                text = "ALL JACKPOTS IN CFA",
                fontFamily = MontserratBold
            )

            JackpotGemsOverlay(
                modifier = Modifier.fillMaxSize(),
                winnerLevel = when (win) {
                    is WinPhase.Focus -> (win as WinPhase.Focus).level
                    is WinPhase.Takeover -> (win as WinPhase.Takeover).level
                    is WinPhase.Rain -> (win as WinPhase.Rain).level
                    else -> null
                },
                winPhase = when (win) {
                    is WinPhase.Focus -> "Focus"
                    is WinPhase.Takeover -> "Takeover"
                    is WinPhase.Rain -> "Rain"
                    else -> "None"
                }
            )

        }

        // Дождь рисуем поверх base-сцены (но до takeover)
        if (!takeover) {
            // Цвет дождя должен быть всегда одинаковый
            val rainColor = Color(0xFFFFE7A3)
            JackpotRain(
                request = rain,
                modifier = Modifier.fillMaxSize(),
                durationMs = 1500,
                particles = 64,
                spreadX = 260f,
                startY = -160f,
                color = rainColor
            )
        }

        // ======== Jackpot positioning/visibility during Focus ========
        val centerYFocus = with(density) { (center2.y).toDp() } // root px -> dp
        val winnerLevel = when (val w = win) {
            is WinPhase.Rain -> w.level
            is WinPhase.Focus -> w.level
            is WinPhase.Takeover -> w.level
            else -> null
        }

        fun alphaFor(level: Int): Float {
            if (win is WinPhase.None) return 1f
            return if (winnerLevel == level) 1f else 0f
        }

        fun yFor(level: Int): androidx.compose.ui.unit.Dp {
            // normal offsets
            val y1 = h * 0.18f
            val y2 = h * 0.38f
            val y3 = h * 0.58f

            if (win is WinPhase.None) return when (level) {
                1 -> y1
                2 -> y2
                else -> y3
            }

            // Focus/Takeover: winner в центр2, остальные всё равно невидимы
            return if (winnerLevel == level) {
                // чуть выше центра, чтобы “сидело” красиво (под titling)
                centerYFocus - 22.dp
            } else {
                when (level) {
                    1 -> y1
                    2 -> y2
                    else -> y3
                }
            }
        }

        fun scaleFor(level: Int): Float {
            if (win is WinPhase.None) return 1f
            return if (winnerLevel == level) 1.10f else 1f
        }

        if (!takeover) {

            val amountGlowColor = when (winnerLevel) {
                1 -> Color(0xFFFF2A2A)
                2 -> Color(0xFFFFE08A)
                3 -> Color(0xFF59E0A7)
                else -> Color.Black
            }

            val isWinAmountGlow = win is WinPhase.Rain || win is WinPhase.Focus
            // Ruby
            val hostOnline = demo.jackpots.isNotEmpty()
            val displayRuby = if (winJackpotLevel==1 && winJackpotAmountMinor!=null && win !is WinPhase.None) winJackpotAmountMinor!! else jackpot1
            if (hostOnline) {
                JackpotAmount(
                    amountMinor = displayRuby,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(1))
                        .graphicsLayerAlphaScale(alphaFor(1), scaleFor(1)),
                    style = TextStyle(fontFamily = ChangoRegular, fontSize = 140.sp),
                    format = MoneyFormat(
                        currency = "",
                        currencyPosition = CurrencyPosition.Prefix,
                        thousandsSeparator = ' ',
                        decimalSeparator = ',',
                        fractionDigits = 0,
                    ),
                    fillColor = Color(0xFFFF0000),
                    shadow = if (isWinAmountGlow && winnerLevel == 1) {
                        TextShadowSpec(amountGlowColor.copy(alpha = 0.62f), Offset(0f, 0f), 42f)
                    } else {
                        TextShadowSpec(Color.Black.copy(alpha = 0.65f), Offset(0f, 11f), 22f)
                    },
                    strokeColor = Color.Black.copy(alpha = 0.18f),
                    strokeWidth = 1.3.dp,
                    maxRollingDigitsFromEnd = 4,
                )
            } else {
                BasicText(
                    text = "★★★★★",
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(1))
                        .graphicsLayerAlphaScale(alphaFor(1), scaleFor(1)),
                    style = TextStyle(
                        fontFamily = ChangoRegular,
                        fontSize = 140.sp,
                        color = Color(0xFFFF0000),
                        textAlign = TextAlign.Center
                    )
                )
            }

            // Gold
            val displayGold = if (winJackpotLevel==2 && winJackpotAmountMinor!=null && win !is WinPhase.None) winJackpotAmountMinor!! else jackpot2
            if (hostOnline) {
                JackpotAmount(
                    amountMinor = displayGold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(2))
                        .graphicsLayerAlphaScale(alphaFor(2), scaleFor(2)),
                    style = TextStyle(fontFamily = ChangoRegular, fontSize = 120.sp),
                    format = MoneyFormat(
                        currency = "",
                        currencyPosition = CurrencyPosition.Prefix,
                        thousandsSeparator = ' ',
                        decimalSeparator = ',',
                        fractionDigits = 0,
                    ),
                    fillColor = Color(0xFFEEC239),
                    shadow = if (isWinAmountGlow && winnerLevel == 2) {
                        TextShadowSpec(amountGlowColor.copy(alpha = 0.58f), Offset(0f, 0f), 38f)
                    } else {
                        TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 8f), 18f)
                    },
                    strokeColor = Color.Black.copy(alpha = 0.14f),
                    strokeWidth = 1.2.dp,
                    maxRollingDigitsFromEnd = 4,
                )
            } else {
                BasicText(
                    text = "★★★",
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(2))
                        .graphicsLayerAlphaScale(alphaFor(2), scaleFor(2)),
                    style = TextStyle(
                        fontFamily = ChangoRegular,
                        fontSize = 120.sp,
                        color = Color(0xFFEEC239),
                        textAlign = TextAlign.Center
                    )
                )
            }

            // Jade
            val displayJade = if (winJackpotLevel==3 && winJackpotAmountMinor!=null && win !is WinPhase.None) winJackpotAmountMinor!! else jackpot3
            if (hostOnline) {
                JackpotAmount(
                    amountMinor = displayJade,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(3))
                        .graphicsLayerAlphaScale(alphaFor(3), scaleFor(3)),
                    style = TextStyle(fontFamily = ChangoRegular, fontSize = 90.sp),
                    format = MoneyFormat(
                        currency = "",
                        currencyPosition = CurrencyPosition.Prefix,
                        thousandsSeparator = ' ',
                        decimalSeparator = ',',
                        fractionDigits = 0,
                    ),
                    fillColor = Color(0xFF28A368),
                    shadow = if (isWinAmountGlow && winnerLevel == 3) {
                        TextShadowSpec(amountGlowColor.copy(alpha = 0.58f), Offset(0f, 0f), 36f)
                    } else {
                        TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 7f), 16f)
                    },
                    strokeColor = Color.Black.copy(alpha = 0.14f),
                    strokeWidth = 1.2.dp,
                    maxRollingDigitsFromEnd = 4,
                )
            } else {
                BasicText(
                    text = "★",
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = yFor(3))
                        .graphicsLayerAlphaScale(alphaFor(3), scaleFor(3)),
                    style = TextStyle(
                        fontFamily = ChangoRegular,
                        fontSize = 90.sp,
                        color = Color(0xFF28A368),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // ======== WIN coins: center2 -> winner box (только в Focus) ========
        if (win is WinPhase.Focus) {
            CoinsLayer(
                burst = winBurst,
                modifier = Modifier.fillMaxSize(),
                // можешь оставить свои “топ” параметры в CoinsLayer по умолчанию,
                // или прокинуть тут (coinsPerSource/sideStepPx/liftPx/etc) — по желанию.
            )
        }

        // ======== Tables ========
        if (!takeover) {
            if (win is WinPhase.None) {
                // нормальный режим: твой TableStage со всеми фичами
                val tableStatesLike = object : TableStatesLike {
                    override fun isActive(table: Int): Boolean {
                        val t = uiTable0(table)
                        return activeTables.contains(t)
                    }
                    override fun hasBetOnBox(table: Int, box: Int): Boolean {
                        val t = uiTable0(table)
                        val b = uiBox0(box)
                        return litBets[t]?.contains(b) == true
                    }
                }

                TableStage(
                    states = tableStatesLike,
                    litBets = litBets,
                    confirmedBurst = confirmedBurst,
                    confirmToken = confirmToken,
                    modifier = Modifier.fillMaxSize(),
                    tableCount = 8,
                    tableHeight = tableHeight,
                    labelTextStyle = labelStyle,
                    confirmFlashMs = 110L
                )
            } else {
                // win mode: показываем только winner box цветом джекпота (и активные столы)
                val w: WinPhase.WithWinData? = when (val cur = win) {
                    is WinPhase.Focus -> cur
                    is WinPhase.Rain -> cur
                    else -> null
                }
                if (w != null) {
                    val winColor = jackpotAccent(w.level)

                    val winnerOnlyStates = object : TableStatesLike {
                        override fun isActive(table: Int): Boolean {
                            val t = uiTable0(table)
                            return activeTables.contains(t)
                        }
                        override fun hasBetOnBox(table: Int, box: Int): Boolean {
                            if (!revealWinnerBox) return false
                            val t = uiTable0(table)
                            val b = uiBox0(box)
                            return (t == w.table && b == w.box)
                        }
                    }

                    TableRowView(
                        states = winnerOnlyStates,
                        tableCount = 8,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        height = tableHeight,
                        labelTextStyle = labelStyle,
                        colors = TableViewColors(
                            active = Color(0xFFFFFFFF),
                            inactive = Color(0x66FFFFFF),
                            bet = winColor,
                            text = Color(0xFFFFFFFF),
                        )
                    )
                }
            }
        }

        // ======== TAKEOVER overlay ========
        if (takeover) {
            val t = win as WinPhase.Takeover
            val bg = jackpotBg(t.level)
            val title = jackpotTitle(t.level)
            val subtitle = "WAS WON BY BOX ${t.box + 1} AT TABLE ${t.table + 1}"

            val tier = remember(t.level) { tierForLevel(t.level) }
            val spec = remember(tier) { tierSpec(tier) }

            // --- layout specs based on H ---
            val gemTop = overlayH * 0.12f
            val gemHeight = overlayH * 0.065f
            val gemBottomGap = overlayH * 0.03f
            val titleBottomGap = overlayH * 0.04f
            val amountBottomGap = overlayH * 0.045f
            val subtitleBottomGap = overlayH * 0.06f

            val titleFont = with(density) { (hPxLocal * 0.048f).toSp() }
            val subtitleFont = with(density) { (hPxLocal * 0.039f).toSp() }
            val amountFont = with(density) { (hPxLocal * 0.13f).toSp() }

            // --- animations (2.5s) ---
            val gemScale = remember { Animatable(1f) }
            val titleAlpha = remember { Animatable(0f) }
            val amountAlpha = remember { Animatable(1f) }
            val tablePulse = remember { Animatable(1f) }
            val counterProgress = remember { Animatable(0f) }
            val introDone = remember { mutableStateOf(false) }

            LaunchedEffect(t.level, t.table, t.box, t.amountWon) {
                introDone.value = false

                // 0.0–0.5: gem bounce + title fade
                gemScale.snapTo(spec.gemIntroFrom)
                titleAlpha.snapTo(0f)
                amountAlpha.snapTo(1f)
                tablePulse.snapTo(1f)
                counterProgress.snapTo(0f)

                // bounce: 0.8 -> 1.1 -> 1.0
                gemScale.animateTo(spec.gemIntroPeak, animationSpec = TweenSpec(durationMillis = spec.gemIntroPeakMs, easing = FastOutSlowInEasing))
                gemScale.animateTo(1.0f, animationSpec = TweenSpec(durationMillis = spec.gemIntroSettleMs, easing = FastOutSlowInEasing))

                // title fade in during first 500ms
                titleAlpha.animateTo(0.90f, animationSpec = TweenSpec(durationMillis = 500, easing = FastOutSlowInEasing))

                // 0.5–~: counter 0 -> final
                counterProgress.animateTo(1f, animationSpec = TweenSpec(durationMillis = spec.counterIntroMs, easing = FastOutSlowInEasing))

                // размер суммы не меняем

                // 2.0s: table pulse
                delay((2000 - (500 + spec.counterIntroMs + spec.impactPulseUpMs + spec.impactPulseDownMs)).coerceAtLeast(0).toLong())
                tablePulse.animateTo(1.1f, animationSpec = TweenSpec(durationMillis = 140, easing = FastOutSlowInEasing))
                tablePulse.animateTo(1f, animationSpec = TweenSpec(durationMillis = 240, easing = FastOutSlowInEasing))

                introDone.value = true
            }

            // Быстрая пульсация альфы суммы (без изменения масштаба)
            LaunchedEffect(t.level, t.table, t.box, t.amountWon) {
                amountAlpha.snapTo(1f)
                // стартуем после интро-кадра счётчика
                delay((500 + spec.counterIntroMs).toLong())
                while (true) {
                    amountAlpha.animateTo(0.82f, animationSpec = TweenSpec(durationMillis = 340, easing = FastOutSlowInEasing))
                    amountAlpha.animateTo(1f, animationSpec = TweenSpec(durationMillis = 340, easing = FastOutSlowInEasing))
                }
            }

            // Idle breathing для amount (после intro)
            val breathe = remember { Animatable(1f) }
            LaunchedEffect(t.level, t.table, t.box, t.amountWon) {
                breathe.snapTo(1f)
                // ждём конец интро
                delay((500 + spec.counterIntroMs + spec.impactPulseUpMs + spec.impactPulseDownMs).toLong())
                while (true) {
                    breathe.animateTo(1f + spec.breatheAmp, animationSpec = TweenSpec(durationMillis = spec.breathePeriodMs / 2, easing = FastOutSlowInEasing))
                    breathe.animateTo(1f, animationSpec = TweenSpec(durationMillis = spec.breathePeriodMs / 2, easing = FastOutSlowInEasing))
                }
            }

            // Idle pulse стола (редко, премиально)
            val tableIdle = remember { Animatable(1f) }
            LaunchedEffect(t.level, t.table, t.box, t.amountWon) {
                tableIdle.snapTo(1f)
                // старт после 2.2s (после коротких частиц)
                delay(2200)
                while (true) {
                    val period = spec.tablePulsePeriodMs
                    // простой редкий pulse
                    tableIdle.animateTo(spec.tablePulseAmp, animationSpec = TweenSpec(durationMillis = 180, easing = FastOutSlowInEasing))
                    tableIdle.animateTo(1f, animationSpec = TweenSpec(durationMillis = 420, easing = FastOutSlowInEasing))
                    delay((period - 600).coerceAtLeast(1200).toLong())
                }
            }

            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) { drawRect(bg) }

                // GOLD image (gem) + glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = gemTop)
                        .height(gemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    val gemRes = when (t.level) {
                        1 -> R.drawable.ruby_195x120
                        2 -> R.drawable.gold_r_205x130
                        else -> R.drawable.jade_r_116x140
                    }

                    // subtle radial glow (15–20% opacity)
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer(alpha = 0.18f)
                    ) {
                        val radius = min(size.width, size.height) * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                radius = radius,
                                center = center
                            ),
                            radius = radius,
                            center = center
                        )
                    }

                    Image(
                        painter = painterResource(gemRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer(scaleX = gemScale.value * 2f, scaleY = gemScale.value * 2f)
                    )
                }

                // Text block (center). Do not change bg color.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val animatedAmount = (t.amountWon.toFloat() * counterProgress.value).roundToLong()
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Spacer(Modifier.height(gemTop + gemHeight + gemBottomGap - overlayH * 0.5f))

                         BasicText(
                             text = title,
                             style = TextStyle(
                                 color = Color.White.copy(alpha = titleAlpha.value),
                                 fontFamily = MontserratBold,
                                 fontSize = titleFont,
                                 letterSpacing = titleFont * 0.09f,
                             )
                         )

                         Spacer(Modifier.height(titleBottomGap))

                         // Fix for centered "0": lock width by measuring final formatted string and using a fixed-width Box.
                         val measurer = rememberTextMeasurer()
                         val amountStyle = remember(amountFont) { TextStyle(fontFamily = ChangoRegular, fontSize = amountFont) }
                         val moneyFmt = remember {
                             MoneyFormat(
                                 currency = "",
                                 currencyPosition = CurrencyPosition.Prefix,
                                 thousandsSeparator = ' ',
                                 decimalSeparator = ',',
                                 fractionDigits = 0,
                                 showCents = false
                             )
                         }
                         val reserveWidthDp = remember(t.amountWon, amountStyle) {
                             val reserveText = formatMoneyFromMinor(t.amountWon, moneyFmt)
                             val res = measurer.measure(
                                 text = AnnotatedString(reserveText),
                                 style = amountStyle.copy(textAlign = TextAlign.Center),
                                 maxLines = 1,
                                 overflow = TextOverflow.Clip,
                                 softWrap = false
                             )
                             with(density) { res.size.width.toDp() }
                         }

                         // Stable centering
                         Box(
                             modifier = Modifier
                                // Visual compensation: some fonts/outlined text have asymmetric glyph bounds ("0" looks shifted right).
                                // Shift amount block slightly left to align with the rest of the centered layout.
                                .offset(x = with(density) { (-30f).toDp() })
                                 .width(reserveWidthDp)
                                 .clip(RoundedCornerShape(0.dp)),
                             contentAlignment = Alignment.Center
                         ) {
                             AmountText(
                                 amountMinor = animatedAmount,
                                 modifier = Modifier.graphicsLayer(alpha = amountAlpha.value),
                                 style = amountStyle,
                                 format = moneyFmt,
                                 fillColor = Color.White,
                                 strokeColor = Color.Transparent,
                                 strokeWidth = 0.dp,
                                 textAlign = TextAlign.Center,
                                 verticalAlign = VerticalAlign.Center,
                                 opticalCentering = true
                             )
                         }

                         Spacer(Modifier.height(amountBottomGap))

                         BasicText(
                             text = subtitle,
                             style = TextStyle(
                                 color = Color.White.copy(alpha = 0.74f),
                                 fontFamily = MontserratBold,
                                 fontSize = subtitleFont,
                                 letterSpacing = subtitleFont * 0.03f,
                             )
                         )

                         Spacer(Modifier.height(subtitleBottomGap))
                     }
                 }

                // Bottom table block: keep position (bottom) and size as in normal.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(text = (t.table + 1).toString(), style = labelStyle)
                        Spacer(Modifier.height(10.dp))

                        val selected = payoutSelectedBox
                        val winnerTableStates = remember(t.box, selected) {
                            object : TableStatesLike {
                                override fun isActive(table: Int): Boolean = true
                                override fun hasBetOnBox(table: Int, box: Int): Boolean {
                                    val b = uiBox0(box)
                                    val sel = selected
                                    if (sel != null && b == sel.second) return false
                                    return (b == t.box)
                                }
                            }
                        }

                        TableRowView(
                            states = winnerTableStates,
                            tableCount = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = tablePulse.value * tableIdle.value,
                                    scaleY = tablePulse.value * tableIdle.value
                                 ),
                            height = tableHeight,
                            labelTextStyle = labelStyle.copy(fontSize = 0.sp),
                            showDotsBetweenTables = false,
                            colors = TableViewColors(
                                active = Color.White,
                                inactive = Color.White.copy(alpha = 0.35f),
                                bet = Color.White,
                                text = Color.White,
                            ),
                            betFillOverride = { _, box ->
                                val b = uiBox0(box)
                                val sel = selected
                                if (sel != null && b == sel.second) null
                                else if (b == t.box) Color.White else null
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------- helpers ----------

private fun jackpotAccent(level: Int): Color = when (level) {
    1 -> Color(0xFFFF2A2A) // ruby
    2 -> Color(0xFFFFE08A) // gold
    else -> Color(0xFF59E0A7) // jade
}

private fun jackpotBg(level: Int): Color = when (level) {
    1 -> Color(0xFF7A0E12) // глубокий рубин
    2 -> Color(0xFF6A4B06) // глубокое золото
    else -> Color(0xFF0A3F2B) // глубокий нефрит
}

private fun jackpotTitle(level: Int): String = when (level) {
    1 -> "RUBY JACKPOT"
    2 -> "GOLD JACKPOT"
    else -> "JADE JACKPOT"
}

private fun Modifier.graphicsLayerAlphaScale(alpha: Float, scale: Float): Modifier =
    this.graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
    }



/**
 * Возвращает центр бокса в ROOT px.
 *
 * Важно: labelFontSizePx передаём уже в px (не sp), чтобы не тащить Density внутрь.
 */
private fun computeBoxCenterInRootPx(
    table: Int,
    box: Int,
    rootW: Float,
    rootH: Float,
    tableHeightPx: Float,
    horizontalPaddingPx: Float,
    bottomPaddingPx: Float,
    labelGapAboveTablePx: Float,
    labelFontSizePx: Float,
    spacingToRadius: Float = 0.30f,
): Offset {
    val n = 8
    val hp = horizontalPaddingPx
    val bp = bottomPaddingPx

    val availableW = (rootW - 2f * hp).coerceAtLeast(0f)

    // оцениваем высоту цифры над столом
    val labelH = (labelFontSizePx * 1.15f).coerceAtLeast(10f)

    // tableH = (2r + s)*3, где s=r*spacingToRadius => factor = (2+spacing)*3
    val spacingFactor = (2f + spacingToRadius)
    val tableFactor = spacingFactor * 3f

    val maxTableH = (tableHeightPx - bp - labelGapAboveTablePx - labelH).coerceAtLeast(0f)

    val rByHeight = if (tableFactor > 0f) maxTableH / tableFactor else 0f
    val rByWidth = if (tableFactor > 0f) availableW / (n * tableFactor) else 0f

    val r = max(0f, min(rByHeight, rByWidth))
    val s = r * spacingToRadius

    val tableW = r * tableFactor
    val tableH = r * tableFactor

    // space-evenly
    val leftoverW = (availableW - n * tableW).coerceAtLeast(0f)
    val gap = leftoverW / (n + 1)

    fun tableLeftX(i: Int) = hp + gap + i * (tableW + gap)

    // panel top in root (таблицы всегда снизу)
    val panelTopInRoot = rootH - tableHeightPx

    // столы внутри панели снизу
    val tableTopYLocal = (tableHeightPx - bp - tableH).coerceAtLeast(0f)

    // boxMap как в TableRowView
    val boxMap = intArrayOf(6, 7, 8, 3, 4, 5, 0, 1, 2)
    val j = boxMap.indexOf(box).coerceAtLeast(0)
    val row = j / 3
    val col = j % 3

    val i = table.coerceIn(0, n - 1)
    val left = tableLeftX(i)

    val xLocal = left + col * (r * 2f + s) + r
    val yLocal = tableTopYLocal + row * (r * 2f + s) + r

    return Offset(xLocal, panelTopInRoot + yLocal)
}

private enum class JackpotTier { Ruby, Gold, Jade }

private data class TierMotionSpec(
     val gemIntroFrom: Float,
     val gemIntroPeak: Float,
     val gemIntroPeakMs: Int,
     val gemIntroSettleMs: Int,
     val counterIntroMs: Int,
     val impactPulsePeak: Float,
     val impactPulseUpMs: Int,
     val impactPulseDownMs: Int,
     val breatheAmp: Float,
     val breathePeriodMs: Int,
     val tablePulseAmp: Float,
     val tablePulsePeriodMs: Int,
     val idleParticles: Int,
     val idleParticleSpeedPxPerSec: Float,
     val idleParticleAlpha: Float,
)

private fun tierForLevel(level: Int): JackpotTier = when (level) {
    1 -> JackpotTier.Ruby
    2 -> JackpotTier.Gold
    else -> JackpotTier.Jade
}

private fun tierSpec(tier: JackpotTier): TierMotionSpec = when (tier) {
    JackpotTier.Ruby -> TierMotionSpec(
        gemIntroFrom = 0.80f,
        gemIntroPeak = 1.15f,
        gemIntroPeakMs = 260,
        gemIntroSettleMs = 240,
        counterIntroMs = 1250,
        impactPulsePeak = 1.08f,
        impactPulseUpMs = 120,
        impactPulseDownMs = 160,
        breatheAmp = 0.025f,
        breathePeriodMs = 3000,
        tablePulseAmp = 1.065f,
        tablePulsePeriodMs = 5400,
        idleParticles = 16,
        idleParticleSpeedPxPerSec = -16f, // немного вверх + дрейф
        idleParticleAlpha = 0.18f,
    )

    JackpotTier.Gold -> TierMotionSpec(
        gemIntroFrom = 0.85f,
        gemIntroPeak = 1.10f,
        gemIntroPeakMs = 280,
        gemIntroSettleMs = 260,
        counterIntroMs = 1500,
        impactPulsePeak = 1.06f,
        impactPulseUpMs = 120,
        impactPulseDownMs = 160,
        breatheAmp = 0.018f,
        breathePeriodMs = 3800,
        tablePulseAmp = 1.055f,
        tablePulsePeriodMs = 7200,
        idleParticles = 12,
        idleParticleSpeedPxPerSec = 10f, // медленно вниз
        idleParticleAlpha = 0.15f,
    )

    JackpotTier.Jade -> TierMotionSpec(
        gemIntroFrom = 0.85f,
        gemIntroPeak = 1.08f,
        gemIntroPeakMs = 300,
        gemIntroSettleMs = 300,
        counterIntroMs = 1750,
        impactPulsePeak = 1.05f,
        impactPulseUpMs = 130,
        impactPulseDownMs = 170,
        breatheAmp = 0.015f,
        breathePeriodMs = 4300,
        tablePulseAmp = 1.045f,
        tablePulsePeriodMs = 8400,
        idleParticles = 9,
        idleParticleSpeedPxPerSec = 8f, // диагональ мягко
        idleParticleAlpha = 0.12f,
    )
}
