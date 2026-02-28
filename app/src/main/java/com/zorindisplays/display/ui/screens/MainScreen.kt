package com.zorindisplays.display.ui.screens

import JackpotGemsOverlay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import com.zorindisplays.display.ui.components.BreathingVignette
import com.zorindisplays.display.ui.components.CurrencyPosition
import com.zorindisplays.display.ui.components.GemSpotlightsOverlay
import com.zorindisplays.display.ui.components.JackpotAmount
import com.zorindisplays.display.ui.components.JackpotSpotlight
import com.zorindisplays.display.ui.components.LuxuryBackground
import com.zorindisplays.display.ui.components.MoneyFormat
import com.zorindisplays.display.ui.components.TextShadowSpec
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MainScreen() {

    // ---- Джекпоты (эмулятор увеличения) ----
    var jackpot1 by remember { mutableStateOf(10_000_000.0) }
    var jackpot2 by remember { mutableStateOf(500_000.0) }
    var jackpot3 by remember { mutableStateOf(100_000.0) }

    val j1 = jackpot1.toLong()
    val j2 = jackpot2.toLong()
    val j3 = jackpot3.toLong()

    // ---- Эмулятор: базово активны 2,3,7; периодически загораются боксы ----
    val baseActiveTables = remember { setOf(2, 3, 7) }

    var litTable by remember { mutableStateOf<Int?>(null) }
    var litBoxes by remember { mutableStateOf<Set<Int>>(emptySet()) } // 1..9

    val tableStatesLike = remember {
        object : TableStatesLike {
            override fun isActive(table: Int): Boolean = baseActiveTables.contains(table)
            override fun hasBetOnBox(table: Int, box: Int): Boolean {
                val t = litTable
                return t != null && table == t && litBoxes.contains(box)
            }
        }
    }

    LaunchedEffect(Unit) {
        val rnd = Random.Default
        val candidates = baseActiveTables.toList()

        while (true) {
            val table = candidates[rnd.nextInt(candidates.size)]
            val count = if (rnd.nextBoolean()) 1 else 2

            val boxes = mutableSetOf<Int>()
            while (boxes.size < count) boxes += rnd.nextInt(1, 10)

            litTable = table
            litBoxes = boxes

            delay(2000)

            litTable = null
            litBoxes = emptySet()

            jackpot1 += 1000.0
            jackpot2 += 1000.0
            jackpot3 += 1000.0

            delay(2000)
        }
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

        // ✅ Камни — между фоном и цифрами
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
            amountMinor = j1,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = h * 0.18f),
            style = TextStyle(
                fontFamily = MontserratBold,
                fontSize = 140.sp,
            ),
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
            amountMinor = j2,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = h * 0.38f),
            style = TextStyle(
                fontFamily = MontserratBold,
                fontSize = 120.sp,
            ),
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
            maxRollingDigitsFromEnd = 4,
        )

        JackpotAmount(
            amountMinor = j3,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = h * 0.58f),
            style = TextStyle(
                fontFamily = MontserratBold,
                fontSize = 90.sp,
            ),
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
            maxRollingDigitsFromEnd = 4,
        )

        TableRowView(
            states = tableStatesLike,
            tableCount = 8,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            labelTextStyle = TextStyle(
                color = Color.White,
                fontSize = 32.sp,
                fontFamily = MontserratBold,
            ),
            height = 240.dp,
        )
    }
}