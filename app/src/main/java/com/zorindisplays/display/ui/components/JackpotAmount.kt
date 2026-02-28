package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JackpotAmount(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: MoneyFormat,
    fillColor: Color,
    shadow: TextShadowSpec,
    strokeColor: Color,
    strokeWidth: Dp,

    // tuning
    peakScale: Float = 1.07f,         // 1.045..1.075
    settleScale: Float = 0.992f,      // 0.985..0.997 (отскок вниз)
    upMs: Int = 75,                   // резкий подъём
    holdPeakMs: Int = 40,             // яркость "на пике"
    downMs: Int = 220,                // плавный спад
    settleMs: Int = 140,              // возврат после отскока
    brightenTo: Float = 0.34f,        // 0.18..0.40 к белому
) {
    val scale = remember { Animatable(1f) }
    val bright = remember { Animatable(0f) } // 0..1
    var prev by remember { mutableStateOf(amountMinor) }

    LaunchedEffect(amountMinor) {
        if (prev == amountMinor) return@LaunchedEffect
        prev = amountMinor

        coroutineScope {
            // SCALE: 1 -> peak (ускорение) -> settle (замедление) -> 1 (замедление)
            launch {
                scale.snapTo(1f)
                scale.animateTo(
                    targetValue = peakScale,
                    animationSpec = tween(upMs, easing = FastOutLinearInEasing)
                )
                // небольшой "стоп-кадр" на максимуме
                delay(holdPeakMs.toLong())
                scale.animateTo(
                    targetValue = settleScale,
                    animationSpec = tween(downMs, easing = LinearOutSlowInEasing)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(settleMs, easing = LinearOutSlowInEasing)
                )
            }

            // BRIGHT: синхронно с пиком scale -> держим -> затухаем вместе со спадом
            launch {
                bright.snapTo(0f)
                bright.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(upMs, easing = FastOutLinearInEasing)
                )
                delay(holdPeakMs.toLong())
                // затухание на всём "спуске" + возврат после отскока
                bright.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(downMs + settleMs, easing = LinearOutSlowInEasing)
                )
            }
        }
    }

    val animatedFill = brightenTowardWhite(
        base = fillColor,
        amount = bright.value * brightenTo
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
        AmountText(
            amountMinor = amountMinor,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            style = style,
            format = format,
            fillColor = animatedFill,
            shadow = shadow,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            textAlign = TextAlign.Center,
            verticalAlign = VerticalAlign.Center,
            opticalCentering = true,
        )
    }
}

private fun brightenTowardWhite(base: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    return Color(
        red = lerp(base.red, 1f, t),
        green = lerp(base.green, 1f, t),
        blue = lerp(base.blue, 1f, t),
        alpha = base.alpha
    )
}