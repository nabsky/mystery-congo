package com.zorindisplays.display.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabsky.mystery.component.TableRowView
import com.nabsky.mystery.component.TableStatesLike
import com.zorindisplays.display.R
import com.zorindisplays.display.ui.components.JackpotAmount
import com.zorindisplays.display.ui.components.CurrencyPosition
import com.zorindisplays.display.ui.components.LuxuryBackground
import com.zorindisplays.display.ui.components.MoneyFormat
import com.zorindisplays.display.ui.components.TextShadowSpec
import com.zorindisplays.display.ui.components.VerticalAlign
import com.zorindisplays.display.ui.theme.MontserratBold
import kotlinx.coroutines.delay
import kotlin.math.min
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

        // ✅ Камни — между фоном и цифрами
        JackpotGemsOverlay(modifier = Modifier.fillMaxSize())

        JackpotAmount(
            amountMinor = j1,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.18f),
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
        )

        JackpotAmount(
            amountMinor = j2,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.38f),
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
        )

        JackpotAmount(
            amountMinor = j3,
            modifier = Modifier.fillMaxWidth() .offset(y = h * 0.58f),
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

@Composable
private fun JackpotGemsOverlay(
    modifier: Modifier = Modifier,
) {
    val ruby = painterResource(R.drawable.ruby_195x120)
    val goldR = painterResource(R.drawable.gold_r_205x130)
    val jadeR = painterResource(R.drawable.jade_r_116x140)

    BoxWithConstraints(modifier = modifier) {
        // Ruby:
        PositionedLayer(
            anchor = Offset(0.79f, 0.15f),
            sizeFrac = 0.22f,
            rotationDeg = 8f,
            alpha = 0.92f,
            blurDp = 0f,
            flipX = false,
        ) { m ->
            Image(
                painter = ruby,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m,
            )
        }

        // Gold
        PositionedLayer(
            anchor = Offset(0.24f, 0.42f),
            sizeFrac = 0.21f,
            alpha = 0.85f,
            blurDp = 1.5f,
            rotationDeg = 8f,
        ) { m ->
            Image(painter = goldR, contentDescription = null, contentScale = ContentScale.Fit, modifier = m)
        }

        // Jade
        PositionedLayer(
            anchor = Offset(0.74f, 0.66f),
            sizeFrac = 0.16f,
            rotationDeg = -10f,
            alpha = 0.74f,
            blurDp = 0f,
        ) { m ->
            Image(painter = jadeR, contentDescription = null, contentScale = ContentScale.Fit, modifier = m)
        }
    }
}

@Composable
private fun PositionedLayer(
    anchor: Offset,
    sizeFrac: Float,
    rotationDeg: Float,
    alpha: Float,
    flipX: Boolean = false,
    blurDp: Float = 0f,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val m = min(w, h)

        val sizePx = m * sizeFrac
        val xPx = w * anchor.x - sizePx / 2f
        val yPx = h * anchor.y - sizePx / 2f

        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizeDp = with(density) { sizePx.toDp() }
        val xDp = with(density) { xPx.toDp() }
        val yDp = with(density) { yPx.toDp() }

        val base = Modifier
            .offset(xDp, yDp)
            .size(sizeDp)
            .graphicsLayer {
                rotationZ = rotationDeg
                this.alpha = alpha
                if (flipX) scaleX = -1f
            }
            .then(
                if (blurDp > 0f)
                    Modifier.blur(blurDp.dp)
                else Modifier
            )

        content(base.fillMaxSize())
    }
}