import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun PositionedLayer(
    anchor: Offset,
    sizeFrac: Float,
    rotationDeg: Float,
    alpha: Float,
    flipX: Boolean = false,
    blurDp: Float = 0f,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val m = min(w, h)

        val sizePx = m * sizeFrac
        val xPx = w * anchor.x - sizePx / 2f
        val yPx = h * anchor.y - sizePx / 2f

        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizeDp = with(density) { sizePx.toDp() }
        val xDp = with(density) { xPx.toDp() }
        val yDp = with(density) { yPx.toDp() }

        val base = Modifier
            .offset(xDp, yDp)
            .size(sizeDp)
            .graphicsLayer {
                rotationZ = rotationDeg
                this.alpha = alpha
                clip = false        // 👈 ВОТ ЭТО КЛЮЧ
                if (flipX) scaleX = -1f
            }
            .then(
                if (blurDp > 0f)
                    Modifier.blur(blurDp.dp)
                else Modifier
            )

        content(base.fillMaxSize())
    }
}