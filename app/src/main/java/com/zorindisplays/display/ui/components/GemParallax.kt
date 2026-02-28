package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Modifier.gemParallax(
    seed: Int,
    ampPx: Float,
    periodMs: Int = 20000,
): Modifier {

    val tr = rememberInfiniteTransition(label = "gemParallax")

    val progress by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "parallaxProgress"
    )

    val angle = computeAngle(seed)
    val phase = computePhase(seed)

    val wave = sin(progress * 2f * PI.toFloat() + phase)

    val dx = cos(angle) * wave * ampPx
    val dy = sin(angle) * wave * ampPx * 0.85f

    return this.then(
        Modifier.graphicsLayer {
            translationX = dx
            translationY = dy
        }
    )
}

private fun computeAngle(seed: Int): Float {
    val x = ((seed * 1103515245L + 12345L) and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
    return x * 2f * PI.toFloat()
}

private fun computePhase(seed: Int): Float {
    val x = (((seed + 1337) * 1664525L + 1013904223L) and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
    return x * 2f * PI.toFloat()
}