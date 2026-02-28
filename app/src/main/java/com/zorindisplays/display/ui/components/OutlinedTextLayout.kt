package com.zorindisplays.display.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.ceil
import kotlin.math.roundToInt

data class TextShadowSpec(
    val color: Color,
    val offset: Offset,
    val blurRadius: Float = 0f // условный "blur": чем больше, тем больше проходов
)

@Composable
fun OutlinedTextLayout(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    fillColor: Color = style.color,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 3.dp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    verticalAlign: VerticalAlign = VerticalAlign.Top,
    opticalCentering: Boolean = false,
    shadow: TextShadowSpec? = null,
    ) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val padPx = ceil(strokePx).toInt()

    val measurer = rememberTextMeasurer()
    val styled = style.copy(textAlign = textAlign)

    Layout(
        content = {},
        modifier = modifier.drawWithCache {
            val innerW = (size.width - 2f * strokePx).coerceAtLeast(0f)
            val innerH = (size.height - 2f * strokePx).coerceAtLeast(0f)

            val layoutResult = measurer.measure(
                text = AnnotatedString(text),
                style = styled,
                maxLines = maxLines,
                overflow = overflow,
                softWrap = softWrap,
                constraints = Constraints(
                    maxWidth = innerW.roundToInt(),
                    maxHeight = innerH.roundToInt()
                )
            )

            val dx = computeDx(
                align = textAlign,
                layoutDirection = layoutDirection,
                containerWidthPx = innerW,
                textWidthPx = layoutResult.size.width.toFloat()
            )

            val dyBase = computeDy(
                align = verticalAlign,
                containerHeightPx = innerH,
                textHeightPx = layoutResult.size.height.toFloat()
            )

            // Оптическая поправка: чуть вверх для капса/цифр
            val dy = if (opticalCentering && verticalAlign == VerticalAlign.Center) {
                val textH = layoutResult.size.height.toFloat()
                val optical = -0.06f * textH   // ~6% высоты текста
                (dyBase + optical).coerceAtLeast(0f)
            } else {
                dyBase
            }

            onDrawBehind {
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(
                        strokePx + dx,
                        strokePx + dy
                    )

                    shadow?.let { sh ->
                        val steps = ((sh.blurRadius / 3f).roundToInt()).coerceIn(1, 8)

                        canvas.save()
                        canvas.translate(sh.offset.x, sh.offset.y)

                        for (i in 1..steps) {
                            val t = i / steps.toFloat()
                            val a = (1f - t) * 0.16f

                            val xJitter = (i - steps / 2f) * 0.15f
                            val ySpread = i * 0.75f

                            canvas.save()
                            canvas.translate(xJitter, ySpread)

                            layoutResult.multiParagraph.paint(
                                canvas = canvas,
                                color = sh.color.copy(alpha = sh.color.alpha * a),
                                drawStyle = Fill,
                            )

                            canvas.restore()
                        }

                        canvas.restore()
                    }

                    layoutResult.multiParagraph.paint(
                        canvas = canvas,
                        color = strokeColor,
                        drawStyle = Stroke(width = strokePx)
                    )
                    layoutResult.multiParagraph.paint(
                        canvas = canvas,
                        color = fillColor,
                        drawStyle = Fill
                    )

                    canvas.restore()
                }
            }
        }
    ) { _, constraints ->
        val inner = constraints.offset(-2 * padPx, -2 * padPx)

        val layoutResult = measurer.measure(
            text = AnnotatedString(text),
            style = styled,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            constraints = inner
        )

        val w = (layoutResult.size.width + 2 * padPx)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (layoutResult.size.height + 2 * padPx)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(w, h) {}
    }
}

private fun computeDx(
    align: TextAlign,
    layoutDirection: LayoutDirection,
    containerWidthPx: Float,
    textWidthPx: Float
): Float {
    val free = (containerWidthPx - textWidthPx).coerceAtLeast(0f)

    fun start() = 0f
    fun end() = free
    fun center() = free / 2f

    return when (align) {
        TextAlign.Left -> start()
        TextAlign.Right -> end()
        TextAlign.Center -> center()
        TextAlign.Start -> if (layoutDirection == LayoutDirection.Ltr) start() else end()
        TextAlign.End -> if (layoutDirection == LayoutDirection.Ltr) end() else start()
        else -> start() // Justify и прочее: оставим как Start
    }
}

private fun computeDy(
    align: VerticalAlign,
    containerHeightPx: Float,
    textHeightPx: Float
): Float {
    val free = (containerHeightPx - textHeightPx).coerceAtLeast(0f)

    return when (align) {
        VerticalAlign.Top -> 0f
        VerticalAlign.Center -> free / 2f
        VerticalAlign.Bottom -> free
    }
}

enum class VerticalAlign {
    Top,
    Center,
    Bottom
}

