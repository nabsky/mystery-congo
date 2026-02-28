package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // --- animation tuning ---
    peakScale: Float = 1.06f,            // 1.045..1.075
    settleScale: Float = 0.992f,         // 0.985..0.997
    upMs: Int = 85,                      // резкий подъём (ускорение)
    holdPeakMs: Int = 45,                // держим пик
    downMs: Int = 220,                   // плавный спад (замедление)
    settleMs: Int = 140,                 // возврат после отскока
    brightenTo: Float = 0.30f,           // 0.18..0.40 (к белому)

    // --- premium look tuning ---
    depthDarken: Float = 0.18f,          // насколько затемнить нижний слой
    topLighten: Float = 0.14f,           // насколько осветлить верхний слой
    highlightAlpha: Float = 0.14f,       // 0.08..0.18
    highlightHeightFrac: Float = 0.36f,  // доля высоты текста для хайлайта
) {
    val scale = remember { Animatable(1f) }
    val bright = remember { Animatable(0f) } // 0..1
    var prev by remember { mutableStateOf(amountMinor) }
    var rollPrev by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(amountMinor) {
        if (prev == amountMinor) return@LaunchedEffect
        rollPrev = prev

        coroutineScope {
            // SCALE: 1 -> peak -> settle -> 1
            launch {
                scale.snapTo(1f)
                scale.animateTo(
                    targetValue = peakScale,
                    animationSpec = tween(upMs, easing = FastOutLinearInEasing)
                )
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

            // BRIGHT: синхронно с пиком scale, держим и затухаем на спаде
            launch {
                bright.snapTo(0f)
                bright.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(upMs, easing = FastOutLinearInEasing)
                )
                delay(holdPeakMs.toLong())
                bright.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(downMs + settleMs, easing = LinearOutSlowInEasing)
                )
            }
        }
        delay(32)
        prev = amountMinor
    }

    val t = (bright.value * brightenTo).coerceIn(0f, 1f)

    // Премиум “градиент” слоями:
    val bottomLayer = mix(fillColor, Color.Black, depthDarken * (0.70f + 0.30f * t))
    val topLayer = mix(fillColor, Color.White, topLighten * (0.60f + 0.40f * t))

    // Чуть “искрить” на пике — но без отдельной вспышки после:
    val highlightColor = Color.White.copy(alpha = highlightAlpha * (0.65f + 0.35f * t))

    val density = LocalDensity.current
    val highlightH = remember(style.fontSize, highlightHeightFrac) {
        // fallback если fontSize не задан
        val px = if (style.fontSize != TextUnit.Unspecified) {
            with(density) { style.fontSize.toPx() }
        } else {
            with(density) { 120.sp.toPx() }
        }
        with(density) { (px * highlightHeightFrac).toDp().coerceIn(18.dp, 72.dp) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
// 1) нижний (темнее) слой — ТЕПЕРЬ fixed-cell, чтобы совпадал по сетке
        val isRolling = rollPrev != null && rollPrev != amountMinor && t > 0.02f
        if (!isRolling) {
            // рисуем нижний depth-слой
            FixedCellAmountText(
                amountMinor = amountMinor,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .graphicsLayer {
                        alpha = 0.92f
                        translationY = 1.4f
                    },
                style = style,
                format = format,
                fillColor = bottomLayer,
                shadow = shadow,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                opticalCentering = true,
            )
        }

// 2) основной слой — rolling
        RollingAmountText(
            amountMinor = amountMinor,
            prevAmountMinor = rollPrev,   // как ты уже сделал
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            style = style,
            format = format,
            fillColor = topLayer,
            shadow = shadow,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            textAlign = TextAlign.Center,
            verticalAlign = VerticalAlign.Center,
            opticalCentering = true,
        )

// 3) highlight — fixed-cell (и клип по высоте как у тебя)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(highlightH)
                .clipToBounds()
        ) {
            FixedCellAmountText(
                amountMinor = amountMinor,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .graphicsLayer { alpha = (t * 0.85f).coerceIn(0f, 1f) },
                style = style,
                format = format,
                fillColor = highlightColor,
                shadow = TextShadowSpec(Color.Transparent, Offset.Zero, 0f),
                strokeColor = Color.Transparent,
                strokeWidth = 0.dp,
                opticalCentering = true,
            )
        }
    }
}

private fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    fun lerp(x: Float, y: Float) = x + (y - x) * k
    return Color(
        red = lerp(a.red, b.red),
        green = lerp(a.green, b.green),
        blue = lerp(a.blue, b.blue),
        alpha = a.alpha
    )
}