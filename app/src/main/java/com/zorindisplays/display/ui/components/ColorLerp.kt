package com.zorindisplays.display.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

internal fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = min(1f, max(0f, t))
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}

internal fun fadeToWhite(from: Color, progress01: Float): Color = lerpColor(from, Color.White, progress01)

