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
    winnerLevel: Int? = null,   // 1=ruby, 2=gold, 3=jade
    winPhase: String = "None",  // "None", "Rain", "Focus", "Takeover"
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

    val isWinGlowPhase = (winPhase == "Rain" || winPhase == "Focus") && winnerColor != null
    val boost = when {
        winPhase == "Rain" && winnerColor != null -> 1.55f
        winPhase == "Focus" && winnerColor != null -> 1.35f
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

            val rubySpotColor = if (isWinGlowPhase) winnerColor!! else rubyColor
            val goldSpotColor = if (isWinGlowPhase) winnerColor!! else goldColor
            val jadeSpotColor = if (isWinGlowPhase) winnerColor!! else jadeColor

            // Ruby
            glow(
                anchor = Offset(0.79f, 0.15f),
                color = rubySpotColor,
                alpha = 0.22f,
                radiusFrac = 0.20f,
                nudgePx = Offset(20f, -20f)
            )

            // Gold
            glow(
                anchor = Offset(0.24f, 0.42f),
                color = goldSpotColor,
                alpha = 0.16f,
                radiusFrac = 0.20f
            )

            // Jade
            glow(
                anchor = Offset(0.74f, 0.66f),
                color = jadeSpotColor,
                alpha = 0.14f,
                radiusFrac = 0.18f
            )
        }
    }
}