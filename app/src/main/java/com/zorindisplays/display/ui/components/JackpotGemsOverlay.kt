import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zorindisplays.display.R
import com.zorindisplays.display.ui.components.gemParallax

@Composable
fun JackpotGemsOverlay(
    modifier: Modifier = Modifier,
) {
    val ruby = painterResource(R.drawable.ruby_195x120)
    val goldR = painterResource(R.drawable.gold_r_205x130)
    val jadeR = painterResource(R.drawable.jade_r_116x140)

    val density = LocalDensity.current
    val ampPxRuby = with(density) { 6.0.dp.toPx() }
    val ampPxGold = with(density) { 5.0.dp.toPx() }
    val ampPxJade = with(density) { 4.0.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {

        PositionedLayer(
            anchor = Offset(0.79f, 0.15f),
            sizeFrac = 0.22f,
            rotationDeg = 8f,
            alpha = 0.92f,
            blurDp = 0f,
            flipX = false,
        ) { m ->
            Image(
                painter = ruby,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m.gemParallax(seed = 11, ampPx = ampPxRuby, periodMs = 9000)
            )
        }

        PositionedLayer(
            anchor = Offset(0.24f, 0.42f),
            sizeFrac = 0.21f,
            alpha = 0.85f,
            blurDp = 1.5f,
            rotationDeg = 8f,
        ) { m ->
            Image(
                painter = goldR,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = m.gemParallax(seed = 22, ampPx = ampPxGold, periodMs = 12000)
            )
        }

        PositionedLayer(
            anchor = Offset(0.74f, 0.66f),
            sizeFrac = 0.16f,
            rotationDeg = -10f,
            alpha = 0.74f,
            blurDp = 0f,
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