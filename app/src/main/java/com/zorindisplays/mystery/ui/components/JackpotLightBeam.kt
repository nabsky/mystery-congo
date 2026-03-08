package com.zorindisplays.mystery.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun JackpotLightBeam(
    modifier: Modifier = Modifier,
    color: Color,
    visible: Boolean
) {
    if (!visible) return

    val tr = rememberInfiniteTransition(label = "jackpotBeam")
    val pulse by tr.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val centerX = w * 0.5f

        val topHalfWidth = w * 0.07f * pulse
        val bottomHalfWidth = w * 0.19f * pulse

        val path = Path().apply {
            moveTo(centerX - topHalfWidth, 0f)
            lineTo(centerX + topHalfWidth, 0f)
            lineTo(centerX + bottomHalfWidth, h)
            lineTo(centerX - bottomHalfWidth, h)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.22f),
                    color.copy(alpha = 0.16f),
                    color.copy(alpha = 0.10f),
                    color.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
        )
    }
}