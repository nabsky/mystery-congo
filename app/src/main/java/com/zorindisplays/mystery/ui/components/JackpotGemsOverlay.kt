package com.zorindisplays.mystery.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zorindisplays.mystery.R
import androidx.compose.ui.geometry.Offset

@Composable
fun JackpotGemsOverlay(
    modifier: Modifier = Modifier,
    winnerLevel: Int? = null, // 1=ruby, 2=gold, 3=jade
    winPhase: String = "None" // "None", "Rain", "Focus", "Takeover"
) {
    val ruby = painterResource(R.drawable.ruby_195x120)
    val goldR = painterResource(R.drawable.gold_r_205x130)
    val jadeR = painterResource(R.drawable.jade_r_116x140)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val ampPxRuby = with(density) { 7.0.dp.toPx() }
    val ampPxGold = with(density) { 6.0.dp.toPx() }
    val ampPxJade = with(density) { 5.0.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val scope = this
        val centerAnchor = Offset(0.5f, 0.38f)
        val winnerAnchorBottom = Offset(0.5f, 0.58f)
        val centerRotation = 0f
        val centerBlur = 0f
        val offscreenAnchor = Offset(-1f, -1f)
        val offscreenAlpha = 0f

        val wPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val hPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val rubyNudgeDxFrac = 20f / wPx
        val rubyNudgeDyFrac = -20f / hPx
        val rubyBaseNone = Offset(0.79f, 0.15f)
        val rubyNoneAnchor = Offset(
            rubyBaseNone.x + rubyNudgeDxFrac,
            rubyBaseNone.y + rubyNudgeDyFrac
        )

        // Ruby
        val rubyParams: Triple<Offset, Float, Float> = when {
            winPhase == "None" -> Triple(rubyNoneAnchor, 8f, 0f)
            (winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 1 -> Triple(winnerAnchorBottom, centerRotation, centerBlur)
            else -> Triple(offscreenAnchor, centerRotation, centerBlur)
        }
        val rubyAlpha = when {
            winPhase == "None" || ((winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 1) -> 0.92f
            else -> offscreenAlpha
        }
        PositionedLayer(
            anchor = rubyParams.first,
            sizeFrac = 0.22f,
            rotationDeg = rubyParams.second,
            alpha = rubyAlpha,
            blurDp = rubyParams.third,
            flipX = false,
        ) { m ->
            Image(
                painter = ruby,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m.gemParallax(seed = 11, ampPx = ampPxRuby, rotationAmpDeg = 0.45f, periodMs = 9000)
            )
        }

        // Gold
        val goldParams: Triple<Offset, Float, Float> = when {
            winPhase == "None" -> Triple(Offset(0.24f, 0.42f), 8f, 1.5f)
            (winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 2 -> Triple(winnerAnchorBottom, centerRotation, centerBlur)
            else -> Triple(offscreenAnchor, centerRotation, centerBlur)
        }
        val goldAlpha = when {
            winPhase == "None" || ((winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 2) -> 0.85f
            else -> offscreenAlpha
        }
        PositionedLayer(
            anchor = goldParams.first,
            sizeFrac = 0.21f,
            alpha = goldAlpha,
            blurDp = goldParams.third,
            rotationDeg = goldParams.second,
        ) { m ->
            Image(
                painter = goldR,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m.gemParallax(seed = 22, ampPx = ampPxGold, periodMs = 12000)
            )
        }

        // Jade
        val jadeParams: Triple<Offset, Float, Float> = when {
            winPhase == "None" -> Triple(Offset(0.74f, 0.66f), -10f, 0f)
            (winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 3 -> Triple(winnerAnchorBottom, centerRotation, centerBlur)
            else -> Triple(offscreenAnchor, centerRotation, centerBlur)
        }
        val jadeAlpha = when {
            winPhase == "None" || ((winPhase == "Rain" || winPhase == "Focus" || winPhase == "Takeover") && winnerLevel == 3) -> 0.74f
            else -> offscreenAlpha
        }
        PositionedLayer(
            anchor = jadeParams.first,
            sizeFrac = 0.16f,
            rotationDeg = jadeParams.second,
            alpha = jadeAlpha,
            blurDp = jadeParams.third,
        ) { m ->
            Image(
                painter = jadeR,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m.gemParallax(seed = 33, ampPx = ampPxJade, periodMs = 15000)
            )
        }
    }
}