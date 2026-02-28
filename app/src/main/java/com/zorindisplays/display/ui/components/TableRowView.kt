package com.nabsky.mystery.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
interface TableStatesLike {
    fun isActive(table: Int): Boolean
    fun hasBetOnBox(table: Int, box: Int): Boolean
}

@Immutable
data class TableViewColors(
    val active: Color,
    val inactive: Color,
    val bet: Color,
    val text: Color,
)

@Composable
fun TableRowView(
    states: TableStatesLike?,
    tableCount: Int,
    modifier: Modifier = Modifier,

    height: Dp = 240.dp,

    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 14.dp,

    spacingToRadius: Float = 0.30f,
    borderToRadius: Float = 0.18f,

    labelTextStyle: TextStyle = TextStyle(color = Color.White),
    labelGapAboveTable: Dp = 10.dp,

    showDotsBetweenTables: Boolean = true,
    dotRadius: Dp = 2.5.dp,

    colors: TableViewColors = TableViewColors(
        active = Color(0xFFFFFFFF),
        inactive = Color(0x66FFFFFF),
        bet = Color(0xFFFFD54F),
        text = Color(0xFFFFFFFF),
    ),

    // NEW: центры боксов в координатах Canvas (локальные для TableRowView)
    onBoxCenters: ((Map<Pair<Int, Int>, Offset>) -> Unit)? = null,
    betFillOverride: ((table: Int, box: Int) -> Color?)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boxMap = remember { intArrayOf(7, 8, 9, 4, 5, 6, 1, 2, 3) }

    val hPx = with(density) { height.toPx() }
    val hp = with(density) { horizontalPadding.toPx() }
    val bp = with(density) { bottomPadding.toPx() }
    val labelGapPx = with(density) { labelGapAboveTable.toPx() }
    val dotRpx = with(density) { dotRadius.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val n = tableCount.coerceAtLeast(0)
        if (n == 0) return@Canvas

        val wPx = size.width
        val availableW = (wPx - 2f * hp).coerceAtLeast(0f)

        val maxLabelText = max(1, n).toString()
        val labelLayout = measurer.measure(
            text = AnnotatedString(maxLabelText),
            style = labelTextStyle.copy(color = if (labelTextStyle.color.isUnspecified) colors.text else labelTextStyle.color)
        )
        val labelH = labelLayout.size.height.toFloat()

        val spacingFactor = (2f + spacingToRadius)
        val tableFactor = spacingFactor * 3f
        val maxTableH = (hPx - bp - labelGapPx - labelH).coerceAtLeast(0f)
        val rByHeight = if (tableFactor > 0f) maxTableH / tableFactor else 0f
        val rByWidth = if (tableFactor > 0f && n > 0) availableW / (n * tableFactor) else 0f

        val r = max(0f, min(rByHeight, rByWidth))
        val s = r * spacingToRadius
        val border = max(1f, r * borderToRadius)

        val tableW = r * tableFactor
        val tableH = r * tableFactor

        val leftoverW = (availableW - n * tableW).coerceAtLeast(0f)
        val gap = leftoverW / (n + 1)

        fun tableLeftX(i: Int) = hp + gap + i * (tableW + gap)
        fun tableCenterX(i: Int) = tableLeftX(i) + tableW / 2f

        val tableTopY = (hPx - bp - tableH).coerceAtLeast(0f)
        val labelCenterY = (tableTopY - labelGapPx - labelH / 2f).coerceAtLeast(0f)

        val centers = if (onBoxCenters != null) LinkedHashMap<Pair<Int, Int>, Offset>(n * 9) else null

        for (i in 0 until n) {
            val left = tableLeftX(i)
            val centerX = tableCenterX(i)

            val isActive = states?.isActive(i + 1) == true

            for (j in 0 until 9) {
                val row = j / 3
                val col = j % 3

                val x = left + col * (r * 2f + s)
                val y = tableTopY + row * (r * 2f + s)
                val c = Offset(x + r, y + r)

                centers?.put((i + 1) to boxMap[j], c)

                val hasBet = isActive && (states?.hasBetOnBox(i + 1, boxMap[j]) == true)

                if (isActive) {
                    if (hasBet) {
                        val fill = betFillOverride?.invoke(i + 1, boxMap[j]) ?: colors.bet
                        drawCircle(fill, r, c, style = Fill)
                    } else {
                        drawCircle(colors.active, r, c, style = Stroke(width = border))
                    }
                } else {
                    drawCircle(colors.inactive, r, c, style = Stroke(width = border))
                }
            }

            drawTextCenteredCompat(
                measurer = measurer,
                text = (i + 1).toString(),
                center = Offset(centerX, labelCenterY),
                style = labelTextStyle.copy(color = if (labelTextStyle.color.isUnspecified) colors.text else labelTextStyle.color)
            )
        }

        if (showDotsBetweenTables && n > 1) {
            for (i in 0 until n - 1) {
                val midX = (tableCenterX(i) + tableCenterX(i + 1)) / 2f
                drawCircle(
                    color = colors.text,
                    radius = dotRpx,
                    center = Offset(midX, labelCenterY),
                    style = Fill
                )
            }
        }

        centers?.let { onBoxCenters?.invoke(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextCenteredCompat(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle
) {
    val res = measurer.measure(AnnotatedString(text), style = style)

    val dx = center.x - res.size.width / 2f
    val dy = center.y - res.size.height / 2f

    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(dx, dy)
        res.multiParagraph.paint(
            canvas = canvas,
            color = style.color
        )
        canvas.restore()
    }
}