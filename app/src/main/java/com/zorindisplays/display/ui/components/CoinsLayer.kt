package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

@Immutable
data class CoinBurst(
    val id: Long = System.nanoTime(),
    val sourcesInRoot: List<Offset>,
    val targetInRoot: Offset,
)

@Composable
fun CoinsLayer(
    modifier: Modifier = Modifier,
    burst: CoinBurst?,
    // tuning
    coinsPerSource: Int = 13,
    durationMs: Int = 1100,

    // траектория
    sideStepPx: Float = 500f,   // сколько “вдоль столов” в начале
    liftPx: Float = 120f,      // насколько “вверх” у второй контрольной точки
    spreadPx: Float = 30f,

    color: Color = Color(0xFFFFE7A3),
) {
    data class Coin(
        val start: Offset,
        val c1: Offset,
        val c2: Offset,
        val end: Offset,
        val bornMs: Long,
        val ttlMs: Int,
        val size: Float,
        val seed: Int,
    )

    var coins by remember { mutableStateOf<List<Coin>>(emptyList()) }

    LaunchedEffect(burst?.id) {
        if (burst == null) return@LaunchedEffect

        val rnd = Random(burst.id)
        val now = nowMs()

        val list = buildList {
            for (src0 in burst.sourcesInRoot) {
                repeat(coinsPerSource) {
                    val src = Offset(
                        x = src0.x + rnd.nextFloat(-spreadPx, spreadPx),
                        y = src0.y + rnd.nextFloat(-spreadPx * 0.25f, spreadPx * 0.25f)
                    )

                    // цель — чуть “гуляет”, но вверху
                    val end = Offset(
                        x = burst.targetInRoot.x + rnd.nextFloat(-40f, 40f),
                        y = burst.targetInRoot.y + rnd.nextFloat(-24f, 24f)
                    )

                    // направление к цели (по X), чтобы “вдоль” было логично
                    val dirX = if (end.x >= src.x) 1f else -1f

                    // c1: почти по линии столов (y ≈ start.y), двигаемся в сторону цели
                    val c1 = Offset(
                        x = src.x + dirX * (sideStepPx + rnd.nextFloat(-18f, 18f)),
                        y = src.y + rnd.nextFloat(-10f, 10f)
                    )

                    // c2: уже заметно выше, ближе к цели по X
                    val c2 = Offset(
                        x = src.x + (end.x - src.x) * 0.65f + rnd.nextFloat(-24f, 24f),
                        y = minOf(src.y, end.y) - liftPx + rnd.nextFloat(-28f, 28f)
                    )

                    add(
                        Coin(
                            start = src,
                            c1 = c1,
                            c2 = c2,
                            end = end,
                            bornMs = now,
                            ttlMs = durationMs + rnd.nextInt(-120, 160),
                            size = rnd.nextFloat(5.5f, 9.5f),
                            seed = rnd.nextInt(),
                        )
                    )
                }
            }
        }

        coins = list
    }

    LaunchedEffect(coins.isNotEmpty()) {
        if (coins.isEmpty()) return@LaunchedEffect
        while (coins.isNotEmpty()) {
            val t = nowMs()
            coins = coins.filter { (t - it.bornMs) < it.ttlMs }
            delay(16)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tNow = nowMs()
        for (c in coins) {
            val t = ((tNow - c.bornMs).toFloat() / c.ttlMs.toFloat()).coerceIn(0f, 1f)

            val e = easeInOutCubic(t)
            val p = cubicBezier(c.start, c.c1, c.c2, c.end, e)

            // хвост: теперь лучше по направлению назад вдоль скорости (приблизим через небольшое смещение)
            val tail = Offset(
                p.x - 20f * (1f - t),
                p.y + 10f * (1f - t),
            )

            val alpha = (1f - t).pow(0.45f) * 0.92f
            val sparkle = 0.65f + 0.35f * sin((t * 12f) + (c.seed % 13) * 0.3f)

            drawLine(
                color = color.copy(alpha = (alpha * 0.22f).coerceIn(0f, 1f)),
                start = tail,
                end = p,
                strokeWidth = (c.size * 0.55f),
            )

            drawCircle(
                color = Color.White.copy(alpha = (alpha * 0.16f * sparkle).coerceIn(0f, 1f)),
                radius = c.size * 1.55f,
                center = p
            )

            drawCircle(
                color = color.copy(alpha = (alpha * 0.95f * sparkle).coerceIn(0f, 1f)),
                radius = c.size,
                center = p
            )
        }
    }
}

private fun cubicBezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t

    val x = (uuu * p0.x) +
            (3f * uu * t * p1.x) +
            (3f * u * tt * p2.x) +
            (ttt * p3.x)

    val y = (uuu * p0.y) +
            (3f * uu * t * p1.y) +
            (3f * u * tt * p2.y) +
            (ttt * p3.y)

    return Offset(x, y)
}

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun nowMs(): Long = System.currentTimeMillis()

private fun Random.nextFloat(min: Float, max: Float): Float =
    min + nextFloat() * (max - min)