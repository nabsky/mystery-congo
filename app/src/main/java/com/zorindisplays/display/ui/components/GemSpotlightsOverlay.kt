package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.min

@Composable
fun GemSpotlightsOverlay(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = min(w, h)

            fun glow(anchor: Offset, color: Color, alpha: Float, radiusFrac: Float) {
                val center = Offset(w * anchor.x, h * anchor.y)
                val r = m * radiusFrac
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }

            // Ruby (верх справа)
            glow(
                anchor = Offset(0.79f, 0.15f),
                color = Color(0xFFFF2A2A),
                alpha = 0.22f,
                radiusFrac = 0.20f
            )

            // Gold (слева)
            glow(
                anchor = Offset(0.24f, 0.42f),
                color = Color(0xFFFFE08A),
                alpha = 0.16f,
                radiusFrac = 0.20f
            )

            // Jade (низ справа)
            glow(
                anchor = Offset(0.74f, 0.66f),
                color = Color(0xFF59E0A7),
                alpha = 0.14f,
                radiusFrac = 0.18f
            )
        }
    }
}