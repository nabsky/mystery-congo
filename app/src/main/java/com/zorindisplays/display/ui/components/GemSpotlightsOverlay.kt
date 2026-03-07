package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.min

@Composable
fun GemSpotlightsOverlay(
    modifier: Modifier = Modifier,
    strength: Float = 1f,
    periodMs: Int = 12000,
    winnerLevel: Int? = null,
    winPhase: String = "None",
) {
    val tr = rememberInfiniteTransition(label = "gemSpotPulse")

    val pulse by tr.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rubyColor = Color(0xFFFF2A2A)
    val goldColor = Color(0xFFFFE08A)
    val jadeColor = Color(0xFF59E0A7)

    val winnerColor = when (winnerLevel) {
        1 -> rubyColor
        2 -> goldColor
        3 -> jadeColor
        else -> null
    }

    val isRain = winPhase == "Rain" && winnerColor != null
    val isFocus = winPhase == "Focus" && winnerColor != null
    val isWinGlowPhase = isRain || isFocus

    val boost = when {
        isRain -> 1.55f
        isFocus -> 1.35f
        else -> 1f
    }

    BoxWithConstraints(modifier = modifier) {
        val scope = this
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = min(w, h)

            fun glow(
                anchor: Offset,
                color: Color,
                alpha: Float,
                radiusFrac: Float,
                nudgePx: Offset = Offset.Zero
            ) {
                val center = Offset(
                    x = w * anchor.x + nudgePx.x,
                    y = h * anchor.y + nudgePx.y
                )
                val r = m * radiusFrac

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = (alpha * boost * pulse * strength).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }

            val rubySpotColor = if (isWinGlowPhase) winnerColor else rubyColor
            val goldSpotColor = if (isWinGlowPhase) winnerColor else goldColor
            val jadeSpotColor = if (isWinGlowPhase) winnerColor else jadeColor

            val rubyAnchor = if (isWinGlowPhase) Offset(0.68f, 0.18f) else Offset(0.79f, 0.15f)
            val goldAnchor = if (isWinGlowPhase) Offset(0.32f, 0.38f) else Offset(0.24f, 0.42f)
            val jadeAnchor = if (isWinGlowPhase) Offset(0.60f, 0.60f) else Offset(0.74f, 0.66f)

            val rubyRadius = if (isRain) 0.24f else if (isFocus) 0.22f else 0.20f
            val goldRadius = if (isRain) 0.23f else if (isFocus) 0.21f else 0.20f
            val jadeRadius = if (isRain) 0.21f else if (isFocus) 0.19f else 0.18f

            glow(
                anchor = rubyAnchor,
                color = rubySpotColor,
                alpha = 0.22f,
                radiusFrac = rubyRadius,
                nudgePx = if (isWinGlowPhase) Offset(10f, -10f) else Offset(20f, -20f)
            )

            glow(
                anchor = goldAnchor,
                color = goldSpotColor,
                alpha = 0.16f,
                radiusFrac = goldRadius
            )

            glow(
                anchor = jadeAnchor,
                color = jadeSpotColor,
                alpha = 0.14f,
                radiusFrac = jadeRadius
            )
        }
    }
}