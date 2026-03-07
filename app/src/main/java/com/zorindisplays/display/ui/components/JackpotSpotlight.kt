package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun JackpotSpotlight(
    modifier: Modifier = Modifier,
    baseColor: Color,
    alpha: Float = 0.20f,
    winnerLevel: Int? = null,
    winPhase: String = "None"
) {
    val rubyColor = Color(0xFFFF2A2A)
    val goldColor = Color(0xFFFFE08A)
    val jadeColor = Color(0xFF59E0A7)

    val winnerColor = when (winnerLevel) {
        1 -> rubyColor
        2 -> goldColor
        3 -> jadeColor
        else -> null
    }

    val spotlightColor =
        if ((winPhase == "Rain" || winPhase == "Focus") && winnerColor != null) winnerColor
        else baseColor

    val alphaBoost = when {
        winPhase == "Rain" && winnerColor != null -> 1.6f
        winPhase == "Focus" && winnerColor != null -> 1.35f
        else -> 1f
    }

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * if (winPhase == "Rain") 0.72f else 0.62f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    spotlightColor.copy(alpha = (alpha * alphaBoost).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}