package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zorindisplays.display.R
import kotlin.math.min

@Composable
fun LuxuryBackground(
    modifier: Modifier = Modifier
) {
    val roulette = painterResource(R.drawable.m_roulette_460x1370)
    val cards = painterResource(R.drawable.m_cards_660x500)
    val dice = painterResource(R.drawable.m_dice_290x210)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val m = min(w, h)

                // 1) базовый почти-чёрный градиент
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF070708), Color(0xFF020203)),
                        center = Offset(w * 0.5f, h * 0.45f),
                        radius = m * 0.95f
                    )
                )

                // 2) очень тонкий "рубиновый" подсвет сверху-центра (Luxury hint)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x14000000), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.32f),
                        radius = m * 0.55f
                    ),
                    blendMode = BlendMode.Screen
                )

                // 3) виньетка
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xD0000000)),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = m * 0.82f
                    )
                )

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x10FFFFFF) // слегка подсветить правый край
                        ),
                        startX = w * 0.75f
                    ),
                    blendMode = BlendMode.Screen
                )

                // 4) лёгкий шум (очень деликатно)
                val step = 28f
                var y = 0f
                while (y < h) {
                    var x = 0f
                    while (x < w) {
                        if (((x.toInt() * 31 + y.toInt() * 17) % 257) == 0) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.02f),
                                radius = 0.8f,
                                center = Offset(x, y),
                                blendMode = BlendMode.Overlay
                            )
                        }
                        x += step
                    }
                    y += step
                }
            }
    ) {
        // Важно: props должны "чувствоваться", но не читаться.

        // Рулетка — большая справа, сильно размытая и приглушённая
        PositionedLayer(
            anchor = Offset(0.96f, 0.45f),
            sizeFrac = 1.26f,
            alpha = 0.15f,
            blurDp = 20f,
            rotationDeg = 0f,
        ) {
            Image(
                painter = roulette,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(0.60f) } // чтобы красное не кричало
                )
            )
        }

        // Карты — слева снизу, мягко и чуть “в движении”
        PositionedLayer(
            anchor = Offset(0.12f, 0.68f),
            sizeFrac = 0.62f,
            rotationDeg = -12f,
            alpha = 0.15f,
            blurDp = 18f,
        ) {
            Image(
                painter = cards,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Кости — слева сверху, небольшой акцент
        PositionedLayer(
            anchor = Offset(0.12f, 0.18f),
            sizeFrac = 0.30f,
            rotationDeg = -6f,
            alpha = 0.18f,
            blurDp = 10f,
        ) {
            Image(
                painter = dice,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Центровой “музейный” свет под цифры (мягкий, очень важно для Luxury)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val m = min(w, h)
                    // было: Color(0x1FFFFFFF), radius = m * 0.65f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x14FFFFFF),  // ✅ слабее
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.52f),
                            radius = m * 0.58f      // ✅ меньше радиус = меньше "дыма"
                        ),
                        blendMode = BlendMode.Screen
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x08FFFFFF),
                                Color.Transparent,
                                Color(0x30000000) // было 0x66 → стало 0x30
                            )
                        ),
                        blendMode = BlendMode.SrcOver // вместо Multiply
                    )
                }
        )
    }
}

@Composable
private fun PositionedLayer(
    anchor: Offset,
    sizeFrac: Float,
    rotationDeg: Float,
    alpha: Float,
    blurDp: Float,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val m = min(w, h)

        val sizePx = m * sizeFrac
        val xPx = w * anchor.x - sizePx / 2f
        val yPx = h * anchor.y - sizePx / 2f

        // переводим px->dp один раз
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizeDp = with(density) { sizePx.toDp() }
        val xDp = with(density) { xPx.toDp() }
        val yDp = with(density) { yPx.toDp() }

        Box(
            modifier = Modifier
                .offset(xDp, yDp)
                .size(sizeDp)
                .graphicsLayer {
                    rotationZ = rotationDeg
                    this.alpha = alpha
                }
                .then(if (blurDp > 0f) Modifier.blur(blurDp.dp) else Modifier)
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}