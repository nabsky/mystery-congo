package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas

@Composable
fun BreathingVignette(
    modifier: Modifier = Modifier,
    strength: Float = 0.22f,     // 0.14..0.30
    periodMs: Int = 8000,        // 6000..11000
) {
    val tr = rememberInfiniteTransition(label = "vignette")
    val a by tr.animateFloat(
        initialValue = strength * 0.85f,
        targetValue = strength,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.78f

        // прозрачный центр + затемнение к краям
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = a)
                ),
                center = c,
                radius = r
            )
        )
    }
}