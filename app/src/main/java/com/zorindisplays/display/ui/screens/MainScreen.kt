package com.zorindisplays.display.ui.screens

import JackpotGemsOverlay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableStatesLike
import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.ui.components.BreathingVignette
import com.zorindisplays.display.ui.components.CurrencyPosition
import com.zorindisplays.display.ui.components.GemSpotlightsOverlay
import com.zorindisplays.display.ui.components.JackpotAmount
import com.zorindisplays.display.ui.components.JackpotSpotlight
import com.zorindisplays.display.ui.components.LuxuryBackground
import com.zorindisplays.display.ui.components.MoneyFormat
import com.zorindisplays.display.ui.components.TableStage
import com.zorindisplays.display.ui.components.TextShadowSpec
import com.zorindisplays.display.ui.components.TopBrandBar
import com.zorindisplays.display.ui.theme.MontserratBold

@Composable
fun MainScreen() {

    val emulator = remember { Emulator().also { it.start() } }
    DisposableEffect(Unit) {
        onDispose { emulator.stop() }
    }

    val demo by emulator.state.collectAsState()

    val activeTables = demo.activeTables
    val litBets = demo.litBets

    val tableStatesLike = object : TableStatesLike {
        override fun isActive(table: Int): Boolean = activeTables.contains(table)
        override fun hasBetOnBox(table: Int, box: Int): Boolean =
            (litBets[table]?.contains(box) == true)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val h = maxHeight

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

        JackpotSpotlight(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .offset(y = h * 0.10f)
                .blur(24.dp),
            color = Color(0xFFFF2A2A),
            alpha = 0.18f
        )

        JackpotAmount(
            amountMinor = demo.jackpot1,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.18f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 140.sp),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0,
            ),
            fillColor = Color(0xFFFF0000),
            shadow = TextShadowSpec(
                color = Color.Black.copy(alpha = 0.65f),
                offset = Offset(0f, 11f),
                blurRadius = 22f,
            ),
            strokeColor = Color.Black.copy(alpha = 0.18f),
            strokeWidth = 1.3.dp,
            maxRollingDigitsFromEnd = 4
        )

        JackpotAmount(
            amountMinor = demo.jackpot2,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.38f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 120.sp),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0,
            ),
            fillColor = Color(0xFFEEC239),
            shadow = TextShadowSpec(
                color = Color.Black.copy(alpha = 0.55f),
                offset = Offset(0f, 8f),
                blurRadius = 18f,
            ),
            strokeColor = Color.Black.copy(alpha = 0.14f),
            strokeWidth = 1.2.dp,
            maxRollingDigitsFromEnd = 4
        )

        JackpotAmount(
            amountMinor = demo.jackpot3,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.58f),
            style = TextStyle(fontFamily = MontserratBold, fontSize = 90.sp),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0,
            ),
            fillColor = Color(0xFF28A368),
            shadow = TextShadowSpec(
                color = Color.Black.copy(alpha = 0.55f),
                offset = Offset(0f, 7f),
                blurRadius = 16f,
            ),
            strokeColor = Color.Black.copy(alpha = 0.14f),
            strokeWidth = 1.2.dp,
            maxRollingDigitsFromEnd = 4
        )

        TableStage(
            states = tableStatesLike,
            litBets = litBets,
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