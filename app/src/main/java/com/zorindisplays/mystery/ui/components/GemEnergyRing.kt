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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

@Composable
fun GemEnergyRing(
    modifier: Modifier = Modifier,
    color: Color,
    visible: Boolean
) {
    if (!visible) return

    val tr = rememberInfiniteTransition(label = "gemEnergyRing")
    val pulse by tr.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rot by tr.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(12000, easing = LinearEasing)
        )
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = min(w, h)

        val center = Offset(w * 0.5f, h * 0.56f)
        val radius = m * 0.09f * pulse
        val stroke = m * 0.006f

        rotate(rot) {
            drawCircle(
                color = color.copy(alpha = 0.28f),
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )

            drawCircle(
                color = color.copy(alpha = 0.12f),
                radius = radius * 1.18f,
                center = center,
                style = Stroke(width = stroke * 0.72f)
            )
        }
    }
}