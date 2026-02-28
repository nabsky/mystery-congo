package com.zorindisplays.display.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun Modifier.gemParallax(
    seed: Int,
    ampPx: Float,
    rotationAmpDeg: Float = 0.35f, // максимум ±0.35°
    periodMs: Int = 12000,
): Modifier {

    val tr = rememberInfiniteTransition(label = "gemParallax")

    val px by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
        ),
        label = "px"
    )

    val py by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((periodMs * 1.37f).toInt(), easing = LinearEasing),
        ),
        label = "py"
    )

    val pr by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((periodMs * 1.83f).toInt(), easing = LinearEasing),
        ),
        label = "pr"
    )

    val phaseX = computePhase(seed * 31 + 7)
    val phaseY = computePhase(seed * 47 + 13)
    val phaseR = computePhase(seed * 59 + 19)

    val dx = sin((px * 2f * PI.toFloat()) + phaseX) * ampPx
    val dy = sin((py * 2f * PI.toFloat()) + phaseY) * (ampPx * 0.75f)
    val rot = sin((pr * 2f * PI.toFloat()) + phaseR) * rotationAmpDeg

    return this.then(
        Modifier.graphicsLayer {
            translationX = dx
            translationY = dy
            rotationZ = rot
        }
    )
}

private fun computePhase(seed: Int): Float {
    val x = ((seed * 1103515245L + 12345L) and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
    return x * 2f * PI.toFloat()
}