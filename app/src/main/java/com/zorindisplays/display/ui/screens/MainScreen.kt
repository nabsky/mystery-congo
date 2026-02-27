package com.zorindisplays.display.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import com.zorindisplays.display.R
import com.zorindisplays.display.ui.components.AmountText
import com.zorindisplays.display.ui.components.CurrencyPosition
import com.zorindisplays.display.ui.components.MoneyFormat
import com.zorindisplays.display.ui.components.VerticalAlign
import com.zorindisplays.display.ui.theme.MontserratBlackItalic
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MainScreen() {

    // ---- Джекпоты (эмулятор увеличения) ----
    var jackpot1 by remember { mutableStateOf(10_000_000.0) }
    var jackpot2 by remember { mutableStateOf(500_000.0) }
    var jackpot3 by remember { mutableStateOf(100_000.0) }

    // ---- Фон: background по умолчанию, background2 как "flash" ----
    var showAlt by remember { mutableStateOf(false) }

    // ---- Эмулятор: базово активны 2,3,7; периодически загораются боксы ----
    // activeTables: постоянные активные столы
    val baseActiveTables = remember { setOf(2, 3, 7) }

    // текущая подсветка (на одном столе 1-2 бокса)
    var litTable by remember { mutableStateOf<Int?>(null) }
    var litBoxes by remember { mutableStateOf<Set<Int>>(emptySet()) } // box numbers: 1..9

    // TableStatesLike для TableRowView
    val tableStatesLike = remember {
        object : TableStatesLike {
            override fun isActive(table: Int): Boolean = baseActiveTables.contains(table)

            override fun hasBetOnBox(table: Int, box: Int): Boolean {
                val t = litTable
                return t != null && table == t && litBoxes.contains(box)
            }
        }
    }

    // ---- Луп эмуляции ----
    LaunchedEffect(Unit) {
        val rnd = Random.Default
        val candidates = baseActiveTables.toList()

        while (true) {
            // Раз в 2 секунды выбираем активный стол и зажигаем 1-2 бокса
            val table = candidates[rnd.nextInt(candidates.size)]
            val count = if (rnd.nextBoolean()) 1 else 2

            // боксы 1..9, без повторов
            val boxes = mutableSetOf<Int>()
            while (boxes.size < count) {
                boxes += rnd.nextInt(1, 10)
            }

            litTable = table
            litBoxes = boxes

            // горят 2 секунды
            delay(2000)

            // гасим
            litTable = null
            litBoxes = emptySet()

            // В момент "стали неактивными": флеш-фон 300мс + джекпоты +1000
            showAlt = true
            jackpot1 += 1000.0
            jackpot2 += 1000.0
            jackpot3 += 1000.0
            delay(300)
            showAlt = false

            // пауза 2 секунды до следующего загорания
            delay(2000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedContent(
            targetState = showAlt,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "bg"
        ) { alt ->
            val lightningRes = remember(alt) {   // или remember(seed)
                if (Random.nextBoolean()) R.drawable.myst_bg_1
                else R.drawable.myst_bg_2
            }
            Image(
                painter = painterResource(
                    if (alt) {
                        lightningRes
                    } else R.drawable.myst_bg_0
                ),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        AmountText(
            amount = jackpot1,
            modifier = Modifier.fillMaxWidth().offset(0.dp, 180.dp),
            style = TextStyle(
                fontFamily = MontserratBlackItalic,
                fontSize = 140.sp
            ),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0
            ),
            fillColor = Color(0xFFFF0000),
            strokeColor = Color.Transparent,
            strokeWidth = 0.dp,
            textAlign = TextAlign.Center,
            verticalAlign = VerticalAlign.Center,
            opticalCentering = true
        )

        AmountText(
            amount = jackpot2,
            modifier = Modifier.fillMaxWidth().offset(0.dp, 420.dp),
            style = TextStyle(
                fontFamily = MontserratBlackItalic,
                fontSize = 120.sp
            ),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0
            ),
            fillColor = Color(0xFFEEC239),
            strokeColor = Color.Transparent,
            strokeWidth = 0.dp,
            textAlign = TextAlign.Center,
            verticalAlign = VerticalAlign.Center,
            opticalCentering = true
        )

        AmountText(
            amount = jackpot3,
            modifier = Modifier.fillMaxWidth().offset(0.dp, 680.dp),
            style = TextStyle(
                fontFamily = MontserratBlackItalic,
                fontSize = 90.sp
            ),
            format = MoneyFormat(
                currency = "",
                currencyPosition = CurrencyPosition.Prefix,
                thousandsSeparator = ' ',
                decimalSeparator = ',',
                fractionDigits = 0
            ),
            fillColor = Color(0xFF28A368),
            strokeColor = Color.Transparent,
            strokeWidth = 0.dp,
            textAlign = TextAlign.Center,
            verticalAlign = VerticalAlign.Center,
            opticalCentering = true
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
                fontFamily = MontserratBold
            ),
            height = 240.dp
        )
    }
}