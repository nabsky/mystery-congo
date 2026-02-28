package com.zorindisplays.display.ui.screens

import JackpotGemsOverlay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableStatesLike
import com.zorindisplays.display.emulator.DemoEvent
import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.ui.components.*
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen() {

    val emulator = remember { Emulator().also { it.start() } }
    DisposableEffect(Unit) { onDispose { emulator.stop() } }

    val demo by emulator.state.collectAsState()

    val tableStatesLike = object : TableStatesLike {
        override fun isActive(table: Int): Boolean = demo.activeTables.contains(table)
        override fun hasBetOnBox(table: Int, box: Int): Boolean =
            demo.litBets[table]?.contains(box) == true
    }

    // WIN сценка
    var winLevel by remember { mutableStateOf<Int?>(null) } // 1..3
    var rain by remember { mutableStateOf<RainRequest?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val h = maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        // центры джекпотов (примерно, без измерений)
        val j1Target = Offset(wPx * 0.5f, hPx * 0.22f)
        val j2Target = Offset(wPx * 0.5f, hPx * 0.42f)
        val j3Target = Offset(wPx * 0.5f, hPx * 0.60f)

        // слушаем win-events
        LaunchedEffect(emulator) {
            emulator.events.collectLatest { e ->
                when (e) {
                    is DemoEvent.JackpotWin -> {
                        winLevel = e.level
                        val target = when (e.level) {
                            1 -> j1Target
                            2 -> j2Target
                            else -> j3Target
                        }
                        rain = RainRequest(target = target)

                        // держим сцену
                        delay(1600)
                        winLevel = null
                        rain = null
                    }
                }
            }
        }

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

        // spotlight усиливаем на winLevel
        fun spotlightAlpha(level: Int, base: Float) =
            if (winLevel == level) (base * 1.8f).coerceAtMost(0.36f) else base

        JackpotSpotlight(
            modifier = Modifier.fillMaxWidth().height(280.dp).offset(y = h * 0.10f).blur(24.dp),
            color = Color(0xFFFF2A2A),
            alpha = spotlightAlpha(1, 0.18f)
        )

        // цифры: на невыигравших можно чуть приглушить (премиально)
        fun dim(level: Int): Float =
            if (winLevel != null && winLevel != level) 0.74f else 1f

        JackpotAmount(
            amountMinor = demo.jackpot1,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.18f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 140.sp),
            format = MoneyFormat(currency = "", currencyPosition = CurrencyPosition.Prefix, thousandsSeparator = ' ', decimalSeparator = ',', fractionDigits = 0),
            fillColor = Color(0xFFFF0000).copy(alpha = dim(1)),
            shadow = TextShadowSpec(Color.Black.copy(alpha = 0.65f), Offset(0f, 11f), 22f),
            strokeColor = Color.Black.copy(alpha = 0.18f),
            strokeWidth = 1.3.dp,
            maxRollingDigitsFromEnd = 4
        )

        JackpotAmount(
            amountMinor = demo.jackpot2,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.38f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 120.sp),
            format = MoneyFormat(currency = "", currencyPosition = CurrencyPosition.Prefix, thousandsSeparator = ' ', decimalSeparator = ',', fractionDigits = 0),
            fillColor = Color(0xFFEEC239).copy(alpha = dim(2)),
            shadow = TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 8f), 18f),
            strokeColor = Color.Black.copy(alpha = 0.14f),
            strokeWidth = 1.2.dp,
            maxRollingDigitsFromEnd = 4
        )

        JackpotAmount(
            amountMinor = demo.jackpot3,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.58f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 90.sp),
            format = MoneyFormat(currency = "", currencyPosition = CurrencyPosition.Prefix, thousandsSeparator = ' ', decimalSeparator = ',', fractionDigits = 0),
            fillColor = Color(0xFF28A368).copy(alpha = dim(3)),
            shadow = TextShadowSpec(Color.Black.copy(alpha = 0.55f), Offset(0f, 7f), 16f),
            strokeColor = Color.Black.copy(alpha = 0.14f),
            strokeWidth = 1.2.dp,
            maxRollingDigitsFromEnd = 4
        )

        // дождь монет поверх сцены
        JackpotRain(
            request = rain,
            modifier = Modifier.fillMaxSize()
        )

        // столы+монеты (confirm-склейка уже внутри)
        TableStage(
            states = tableStatesLike,
            litBets = demo.litBets,
            modifier = Modifier.fillMaxSize(),
            tableCount = 8,
            tableHeight = 240.dp,
            labelTextStyle = TextStyle(
                color = Color.White,
                fontSize = 32.sp,
                fontFamily = MontserratBold,
            ),
            confirmFlashMs = 110L
        )
    }
}