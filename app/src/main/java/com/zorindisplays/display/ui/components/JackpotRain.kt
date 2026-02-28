package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

@Immutable
data class RainRequest(
    val id: Long = System.nanoTime(),
    val target: Offset,      // в root px
)

@Composable
fun JackpotRain(
    modifier: Modifier = Modifier,
    request: RainRequest?,
    durationMs: Int = 1400,
    particles: Int = 42,
    spreadX: Float = 210f,
    startY: Float = -120f,
    color: Color = Color(0xFFFFE7A3),
) {
    data class P(
        val x0: Float,
        val y0: Float,
        val vx: Float,
        val vy: Float,
        val r: Float,
        val born: Long,
        val ttl: Int,
        val seed: Int,
    )

    var ps by remember { mutableStateOf<List<P>>(emptyList()) }

    LaunchedEffect(request?.id) {
        if (request == null) return@LaunchedEffect
        val rnd = Random(request.id)
        val now = nowMs()

        ps = List(particles) {
            val x0 = request.target.x + rnd.nextFloat(-spreadX, spreadX)
            val y0 = startY + rnd.nextFloat(-40f, 60f)
            val vx = rnd.nextFloat(-30f, 30f)
            val vy = rnd.nextFloat(520f, 860f)
            val r = rnd.nextFloat(4.5f, 9.5f)
            P(x0, y0, vx, vy, r, now, durationMs + rnd.nextInt(-180, 220), rnd.nextInt())
        }
    }

    LaunchedEffect(ps.isNotEmpty()) {
        if (ps.isEmpty()) return@LaunchedEffect
        while (ps.isNotEmpty()) {
            val t = nowMs()
            ps = ps.filter { (t - it.born) < it.ttl }
            delay(16)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tNow = nowMs()
        for (p in ps) {
            val t = ((tNow - p.born).toFloat() / p.ttl.toFloat()).coerceIn(0f, 1f)
            val e = easeOutCubic(t)

            val x = p.x0 + p.vx * e
            val y = p.y0 + p.vy * e

            // “впитывание” около цели: плавно гасим ближе к концу
            val a = (1f - t).pow(0.35f) * 0.85f
            val sparkle = 0.7f + 0.3f * kotlin.math.sin(t * 10f + (p.seed % 7) * 0.4f)

            drawCircle(
                color = Color.White.copy(alpha = (a * 0.14f * sparkle).coerceIn(0f, 1f)),
                radius = p.r * 1.6f,
                center = Offset(x, y)
            )
            drawCircle(
                color = color.copy(alpha = (a * 0.95f * sparkle).coerceIn(0f, 1f)),
                radius = p.r,
                center = Offset(x, y)
            )
        }
    }
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)
private fun nowMs(): Long = System.currentTimeMillis()
private fun Random.nextFloat(min: Float, max: Float): Float = min + nextFloat() * (max - min)