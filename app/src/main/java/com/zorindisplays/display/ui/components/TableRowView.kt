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

    // фиксированная высота компонента (ставь внизу через align(Alignment.BottomCenter))
    height: Dp = 240.dp,

    // отступы
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 14.dp,

    // геометрия кружков
    spacingToRadius: Float = 0.30f,   // как в Java: s = r * 0.3
    borderToRadius: Float = 0.18f,    // толщина обводки = r * 0.18

    // цифра над столом (СТИЛЬ И РАЗМЕР НЕ ТРОГАЕМ)
    labelTextStyle: TextStyle = TextStyle(color = Color.White),
    labelGapAboveTable: Dp = 10.dp,

    // точки между цифрами
    showDotsBetweenTables: Boolean = true,
    dotRadius: Dp = 2.5.dp,

    colors: TableViewColors = TableViewColors(
        active = Color(0xFFFFFFFF),
        inactive = Color(0x66FFFFFF),
        bet = Color(0xFFFFD54F),
        text = Color(0xFFFFFFFF),
    ),
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

        // --- 1) измеряем высоту цифры (по твоему labelTextStyle)
        // берём самый широкий/высокий текст для стабильности: "88" или max номер стола
        val maxLabelText = max(1, n).toString()
        val labelLayout = measurer.measure(
            text = AnnotatedString(maxLabelText),
            style = labelTextStyle.copy(color = if (labelTextStyle.color.isUnspecified) colors.text else labelTextStyle.color)
        )
        val labelH = labelLayout.size.height.toFloat()

        // --- 2) считаем максимально возможный r по оставшейся высоте
        // tableH = (2r + s)*3, s=r*spacingToRadius => tableH = r*(2+spacing)*3
        val spacingFactor = (2f + spacingToRadius)
        val tableFactor = spacingFactor * 3f // и для ширины, и для высоты
        val maxTableH = (hPx - bp - labelGapPx - labelH).coerceAtLeast(0f)
        val rByHeight = if (tableFactor > 0f) maxTableH / tableFactor else 0f

        // --- 3) ограничение по ширине: чтобы влезли N столов (gap может быть 0, а потом посчитаем space-evenly)
        val rByWidth = if (tableFactor > 0f && n > 0) availableW / (n * tableFactor) else 0f

        val r = max(0f, min(rByHeight, rByWidth))
        val s = r * spacingToRadius
        val border = max(1f, r * borderToRadius)

        val tableW = r * tableFactor
        val tableH = r * tableFactor

        // --- 4) space-evenly: (N+1) одинаковых gap’ов
        val leftoverW = (availableW - n * tableW).coerceAtLeast(0f)
        val gap = leftoverW / (n + 1)

        fun tableLeftX(i: Int) = hp + gap + i * (tableW + gap)
        fun tableCenterX(i: Int) = tableLeftX(i) + tableW / 2f

        // --- 5) позиция по Y: столы снизу, цифры над ними
        val tableTopY = (hPx - bp - tableH).coerceAtLeast(0f)
        val labelCenterY = (tableTopY - labelGapPx - labelH / 2f).coerceAtLeast(0f)

        // --- 6) рисование
        for (i in 0 until n) {
            val left = tableLeftX(i)
            val centerX = tableCenterX(i)

            val isActive = states?.isActive(i + 1) == true

            // 3x3 кружки
            for (j in 0 until 9) {
                val row = j / 3
                val col = j % 3

                val x = left + col * (r * 2f + s)
                val y = tableTopY + row * (r * 2f + s)
                val c = Offset(x + r, y + r)

                val hasBet = isActive && (states?.hasBetOnBox(i + 1, boxMap[j]) == true)

                if (isActive) {
                    if (hasBet) {
                        drawCircle(colors.bet, r, c, style = Fill)
                    } else {
                        drawCircle(colors.active, r, c, style = Stroke(width = border))
                    }
                } else {
                    drawCircle(colors.inactive, r, c, style = Stroke(width = border))
                }
            }

            // цифра над столом (стиль фиксированный)
            drawTextCenteredCompat(
                measurer = measurer,
                text = (i + 1).toString(),
                center = Offset(centerX, labelCenterY),
                style = labelTextStyle.copy(color = if (labelTextStyle.color.isUnspecified) colors.text else labelTextStyle.color)
            )
        }

        // точки между цифрами — в середине между соседними центрами
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
    }
}

/**
 * Совместимо со старыми версиями Compose: без topLeft.
 */
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