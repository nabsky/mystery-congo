package com.zorindisplays.display.ui.screens

import JackpotGemsOverlay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import com.nabsky.mystery.component.TableViewColors
import com.zorindisplays.display.R
import com.zorindisplays.display.emulator.DemoEvent
import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.ui.components.*
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.min

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
fun MainScreen() {

    val emulator = remember { Emulator().also { it.start() } }
    DisposableEffect(Unit) { onDispose { emulator.stop() } }

    val demo by emulator.state.collectAsState()

    var win by remember { mutableStateOf<WinPhase>(WinPhase.None) }

    // burst монет из центра джекпотов -> в выигравший бокс
    var winBurst by remember { mutableStateOf<CoinBurst?>(null) }
    var rain by remember { mutableStateOf<RainRequest?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

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
        LaunchedEffect(emulator) {
            emulator.events.collectLatest { e ->
                when (e) {
                    is DemoEvent.JackpotWin -> {
                        emulator.setPaused(true)

                        // (1) Rain: дождь сверху в центр зоны джекпотов (без takeover-фона)
                        win = WinPhase.Rain(e.level, e.table, e.box, e.amountWon)
                        rain = RainRequest(target = rainTarget)
                        delay(1500)
                        rain = null

                        // (2) Focus: оставляем только нужный джекпот + (3) монеты в winner box
                        win = WinPhase.Focus(e.level, e.table, e.box, e.amountWon)

                        // ждём кадр чтобы посчитать координаты/спавнить монеты
                        delay(16)

                        val winnerCenterInRoot = computeBoxCenterInRootPx(
                            table = e.table,
                            box = e.box,
                            rootW = wPx,
                            rootH = hPx,
                            tableHeightPx = tableHeightPx,
                            horizontalPaddingPx = hpPx,
                            bottomPaddingPx = bpPx,
                            labelGapAboveTablePx = labelGapPx,
                            labelFontSizePx = labelFontPx,
                            spacingToRadius = 0.30f
                        )

                        winBurst = CoinBurst(
                            sourcesInRoot = listOf(rainTarget),
                            targetInRoot = winnerCenterInRoot
                        )

                        delay(1500)

                        // (4) Takeover: фон + текст (без дождя)
                        win = WinPhase.Takeover(e.level, e.table, e.box, e.amountWon)
                        winBurst = null

                        // держим takeover 10 секунд
                        delay(10_000)

                        emulator.resetJackpot(e.level)
                        win = WinPhase.None
                        emulator.setPaused(false)
                    }
                }
            }
        }

        val h = maxHeight

        // ======== Base scene layers (не показываем, когда Takeover) ========
        val takeover = win is WinPhase.Takeover
        val focus = win is WinPhase.Focus
        // val raining = win is WinPhase.Rain // не используется

        if (!takeover) {
            LuxuryBackground(modifier = Modifier.fillMaxSize())
            GemSpotlightsOverlay(modifier = Modifier.fillMaxSize())

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

            JackpotGemsOverlay(modifier = Modifier.fillMaxSize())

            // spotlight (можно усилить на focus)
            val spotAlpha = if (focus && (win as WinPhase.Focus).level == 1) 0.30f else 0.18f
            JackpotSpotlight(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .offset(y = h * 0.10f)
                    .blur(24.dp),
                color = Color(0xFFFF2A2A),
                alpha = spotAlpha
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
            // Ruby
            JackpotAmount(
                amountMinor = demo.jackpot1,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = yFor(1))
                    .graphicsLayerAlphaScale(alphaFor(1), scaleFor(1)),
                style = TextStyle(fontFamily = MontserratBold, fontSize = 140.sp),
                format = MoneyFormat(
                    currency = "",
                    currencyPosition = CurrencyPosition.Prefix,
                    thousandsSeparator = ' ',
                    decimalSeparator = ',',
                    fractionDigits = 0,
                ),
                fillColor = Color(0xFFFF0000),
                shadow = TextShadowSpec(Color.Black.copy(alpha = 0.65f), Offset(0f, 11f), 22f),
                strokeColor = Color.Black.copy(alpha = 0.18f),
                strokeWidth = 1.3.dp,
                maxRollingDigitsFromEnd = 4,
            )

            // Gold
            JackpotAmount(
                amountMinor = demo.jackpot2,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = yFor(2))
                    .graphicsLayerAlphaScale(alphaFor(2), scaleFor(2)),
                style = TextStyle(fontFamily = MontserratBold, fontSize = 120.sp),
                format = MoneyFormat(
                    currency = "",
                    currencyPosition = CurrencyPosition.Prefix,
                    thousandsSeparator = ' ',
                    decimalSeparator = ',',
                    fractionDigits = 0,
                ),
                fillColor = Color(0xFFEEC239),
                shadow = TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 8f), 18f),
                strokeColor = Color.Black.copy(alpha = 0.14f),
                strokeWidth = 1.2.dp,
                maxRollingDigitsFromEnd = 4,
            )

            // Jade
            JackpotAmount(
                amountMinor = demo.jackpot3,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = yFor(3))
                    .graphicsLayerAlphaScale(alphaFor(3), scaleFor(3)),
                style = TextStyle(fontFamily = MontserratBold, fontSize = 90.sp),
                format = MoneyFormat(
                    currency = "",
                    currencyPosition = CurrencyPosition.Prefix,
                    thousandsSeparator = ' ',
                    decimalSeparator = ',',
                    fractionDigits = 0,
                ),
                fillColor = Color(0xFF28A368),
                shadow = TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 7f), 16f),
                strokeColor = Color.Black.copy(alpha = 0.14f),
                strokeWidth = 1.2.dp,
                maxRollingDigitsFromEnd = 4,
            )
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
                    override fun isActive(table: Int): Boolean = demo.activeTables.contains(table)
                    override fun hasBetOnBox(table: Int, box: Int): Boolean =
                        demo.litBets[table]?.contains(box) == true
                }

                TableStage(
                    states = tableStatesLike,
                    litBets = demo.litBets,
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
                        override fun isActive(table: Int): Boolean = demo.activeTables.contains(table)
                        override fun hasBetOnBox(table: Int, box: Int): Boolean =
                            (table == w.table && box == w.box)
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
            val subtitle = "WAS WON BY BOX ${t.box} AT TABLE ${t.table}"

            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) { drawRect(bg) }

                // Титры (по центру, как раньше)
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Камень прямо над текстом
                        val gemRes = when (t.level) {
                            1 -> R.drawable.ruby_195x120
                            2 -> R.drawable.gold_r_205x130
                            else -> R.drawable.jade_r_116x140
                        }
                        Image(
                            painter = painterResource(gemRes),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(74.dp)
                                .padding(bottom = 12.dp)
                        )

                        BasicText(
                            text = title,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.92f),
                                fontFamily = MontserratBold,
                                fontSize = 26.sp,
                                letterSpacing = 4.sp,
                            )
                        )

                        Spacer(Modifier.height(18.dp))

                        AmountText(
                            amountMinor = t.amountWon,
                            modifier = Modifier,
                            style = TextStyle(
                                fontFamily = MontserratBold,
                                fontSize = 110.sp,
                            ),
                            format = MoneyFormat(
                                currency = "",
                                currencyPosition = CurrencyPosition.Prefix,
                                thousandsSeparator = ' ',
                                decimalSeparator = ',',
                                fractionDigits = 0,
                                showCents = false
                            ),
                            fillColor = Color.White,
                            strokeColor = Color.Transparent,
                            strokeWidth = 0.dp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            verticalAlign = VerticalAlign.Center,
                            opticalCentering = true
                        )

                        Spacer(Modifier.height(16.dp))

                        BasicText(
                            text = subtitle,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.86f),
                                fontFamily = MontserratBold,
                                fontSize = 20.sp,
                                letterSpacing = 1.6.sp,
                            )
                        )
                    }
                }

                // Низ: только один выигравший стол по центру, размер как в обычном режиме
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Номер выигравшего стола
                        BasicText(
                            text = t.table.toString(),
                            style = labelStyle
                        )

                        Spacer(Modifier.height(10.dp))

                        val winnerTableStates = remember(t.box) {
                            object : TableStatesLike {
                                override fun isActive(table: Int): Boolean = true
                                override fun hasBetOnBox(table: Int, box: Int): Boolean = (box == t.box)
                            }
                        }

                        TableRowView(
                            states = winnerTableStates,
                            tableCount = 1,
                            modifier = Modifier.fillMaxWidth(),
                            height = tableHeight,
                            // лейбл внутри TableRowView отключаем, т.к. он всегда "1"
                            labelTextStyle = labelStyle.copy(fontSize = 0.sp),
                            showDotsBetweenTables = false,
                            colors = TableViewColors(
                                active = Color.White,
                                inactive = Color.White.copy(alpha = 0.35f),
                                bet = Color.White,
                                text = Color.White,
                            ),
                            betFillOverride = { _, box -> if (box == t.box) Color.White else null },
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
    val boxMap = intArrayOf(7, 8, 9, 4, 5, 6, 1, 2, 3)
    val j = boxMap.indexOf(box).coerceAtLeast(0)
    val row = j / 3
    val col = j % 3

    val i = (table - 1).coerceIn(0, n - 1)
    val left = tableLeftX(i)

    val xLocal = left + col * (r * 2f + s) + r
    val yLocal = tableTopYLocal + row * (r * 2f + s) + r

    return Offset(xLocal, panelTopInRoot + yLocal)
}