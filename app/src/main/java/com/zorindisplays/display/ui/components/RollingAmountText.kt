package com.zorindisplays.display.ui.components

import android.R.attr.translationY
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rolling digits for amount string. Animates only digits that changed.
 * Layout is stable: each digit uses fixed width = max width among 0..9.
 */
@Composable
fun RollingAmountText(
    amountMinor: Long,
    prevAmountMinor: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: MoneyFormat,
    fillColor: Color,
    shadow: TextShadowSpec,
    strokeColor: Color,
    strokeWidth: Dp,
    textAlign: TextAlign = TextAlign.Center,
    verticalAlign: VerticalAlign = VerticalAlign.Center,
    opticalCentering: Boolean = true,

    // animation tuning
    rollUpMs: Int = 140,
    rollDownMs: Int = 260,
    maxRollingDigitsFromEnd: Int = 4
) {
    val newText = remember(amountMinor, format) { formatMoneyFromMinorPublic(amountMinor, format) }
    val oldText = remember(prevAmountMinor, format) {
        prevAmountMinor?.let { formatMoneyFromMinorPublic(it, format) }
    }

    // Normalize lengths so positions match (pad left with spaces)
    val maxLen = max(newText.length, oldText?.length ?: 0)
    val newNorm = newText.padStart(maxLen, ' ')
    val oldNorm = (oldText ?: newText).padStart(maxLen, ' ')

    val canRoll = remember(newNorm, maxRollingDigitsFromEnd) {
        computeRollingMaskFromEnd(newNorm, maxRollingDigitsFromEnd)
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }

    // Fixed digit cell width (prevents number "wobble" when digits change)
    val digitCellWidthPx = remember(style, format, strokePx) {
        var w = 0
        for (ch in '0'..'9') {
            val r = measurer.measure(AnnotatedString(ch.toString()), style = style)
            w = max(w, r.size.width)
        }
        // add stroke padding + small safety
        (w + (strokePx * 2f) + 2f).roundToInt()
    }

    // Height for rolling viewport
    val cellHeightPx = remember(style, format) {
        val r = measurer.measure(AnnotatedString("0"), style = style)
        max(1, r.size.height)
    }

    val digitCellWidthDp = with(density) { digitCellWidthPx.toDp() }
    val cellHeightDp = with(density) { cellHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until maxLen) {
                val newCh = newNorm[i]
                val oldCh = oldNorm[i]

                val isDigit = newCh.isDigit() && oldCh.isDigit()
                val changed = newCh != oldCh

                val width = if (newCh.isDigit()) digitCellWidthDp else measuredCharWidthDp(
                    ch = newCh,
                    measurer = measurer,
                    style = style,
                    strokePx = strokePx,
                )

                if (isDigit && changed && canRoll[i]) {
                    RollingChar(
                        oldCh = oldCh,
                        newCh = newCh,
                        width = width,
                        height = cellHeightDp,
                        style = style,
                        fillColor = fillColor,
                        shadow = shadow,
                        strokeColor = strokeColor,
                        strokeWidth = strokeWidth,
                        textAlign = textAlign,
                        verticalAlign = verticalAlign,
                        opticalCentering = opticalCentering,
                        rollUpMs = rollUpMs,
                        rollDownMs = rollDownMs,
                    )
                } else {
                    // static char (or unchanged digit)
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(cellHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedTextLayout(
                            text = newCh.toString(),
                            modifier = Modifier,
                            style = style.copy(textAlign = TextAlign.Center),
                            shadow = shadow,
                            fillColor = fillColor,
                            strokeColor = strokeColor,
                            strokeWidth = strokeWidth,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            verticalAlign = VerticalAlign.Center,
                            opticalCentering = opticalCentering,
                        )
                    }
                }
            }
        }
    }
}

private fun computeRollingMaskFromEnd(text: String, maxDigits: Int): BooleanArray {
    val mask = BooleanArray(text.length)
    var digits = 0
    for (i in text.length - 1 downTo 0) {
        val ch = text[i]
        if (ch.isDigit()) {
            digits++
            if (digits <= maxDigits) mask[i] = true
        }
    }
    return mask
}

@Composable
private fun RollingChar(
    oldCh: Char,
    newCh: Char,
    width: Dp,
    height: Dp,
    style: TextStyle,
    fillColor: Color,
    shadow: TextShadowSpec,
    strokeColor: Color,
    strokeWidth: Dp,
    textAlign: TextAlign,
    verticalAlign: VerticalAlign,
    opticalCentering: Boolean,
    rollUpMs: Int,
    rollDownMs: Int,
) {
    val progress = remember { Animatable(0f) }

    // ✅ вычисляем px один раз в composable-контексте
    val density = LocalDensity.current
    val heightPx = remember(height, density) { with(density) { height.toPx() } }

    LaunchedEffect(oldCh, newCh) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = rollUpMs + rollDownMs,
                easing = LinearOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val dy = heightPx * progress.value

        // old (уезжает вверх)
        Box(
            modifier = Modifier.graphicsLayer { translationY = -dy },
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextLayout(
                text = oldCh.toString(),
                modifier = Modifier,
                style = style.copy(textAlign = TextAlign.Center),
                shadow = shadow,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                softWrap = false,
                textAlign = TextAlign.Center,
                verticalAlign = VerticalAlign.Center,
                opticalCentering = opticalCentering,
            )
        }

        // new (заезжает снизу)
        Box(
            modifier = Modifier.graphicsLayer { translationY = (heightPx - dy) },
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextLayout(
                text = newCh.toString(),
                modifier = Modifier,
                style = style.copy(textAlign = TextAlign.Center),
                shadow = shadow,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                softWrap = false,
                textAlign = TextAlign.Center,
                verticalAlign = VerticalAlign.Center,
                opticalCentering = opticalCentering,
            )
        }
    }
}

@Composable
private fun measuredCharWidthDp(
    ch: Char,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    strokePx: Float,
): Dp {
    val density = LocalDensity.current
    val w = remember(ch, style, strokePx) {
        val r = measurer.measure(AnnotatedString(ch.toString()), style = style)
        // stroke + small safety
        (r.size.width + (strokePx * 2f) + 2f).roundToInt()
    }
    return with(density) { w.toDp() }
}

/**
 * Same formatting as in AmountText.kt, but public for RollingAmountText.
 */
@Stable
fun formatMoneyFromMinorPublic(minor: Long, format: MoneyFormat): String {
    val negative = minor < 0
    val abs = kotlin.math.abs(minor)

    val factor = pow10(format.fractionDigits)
    val major = abs / factor
    val frac = (abs % factor).toInt()

    val majorStr = groupThousands(major.toString(), format.thousandsSeparator)

    val amountStr = if (format.showCents && format.fractionDigits > 0) {
        val fracStr = frac.toString().padStart(format.fractionDigits, '0')
        "$majorStr${format.decimalSeparator}$fracStr"
    } else {
        majorStr
    }

    val sign = if (negative) "-" else ""
    val space = if (format.spaceBetweenCurrencyAndAmount) " " else ""

    return when (format.currencyPosition) {
        CurrencyPosition.Prefix -> "$sign${format.currency}$space$amountStr"
        CurrencyPosition.Suffix -> "$sign$amountStr$space${format.currency}"
    }
}

private fun groupThousands(raw: String, sep: Char): String {
    val sb = StringBuilder(raw.length + raw.length / 3)
    var count = 0
    for (i in raw.length - 1 downTo 0) {
        sb.append(raw[i])
        count++
        if (count == 3 && i != 0) {
            sb.append(sep)
            count = 0
        }
    }
    return sb.reverse().toString()
}

private fun pow10(n: Int): Long {
    var p = 1L
    repeat(n.coerceAtLeast(0)) { p *= 10L }
    return p
}

@Composable
fun FixedCellAmountText(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: MoneyFormat,
    fillColor: Color,
    shadow: TextShadowSpec,
    strokeColor: Color,
    strokeWidth: Dp,
    opticalCentering: Boolean = true,
) {
    val text = remember(amountMinor, format) { formatMoneyFromMinorPublic(amountMinor, format) }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }

    val digitCellWidthPx = remember(style, strokePx) {
        var w = 0
        for (ch in '0'..'9') {
            val r = measurer.measure(AnnotatedString(ch.toString()), style = style)
            w = max(w, r.size.width)
        }
        (w + (strokePx * 2f) + 2f).roundToInt()
    }

    val cellHeightPx = remember(style) {
        val r = measurer.measure(AnnotatedString("0"), style = style)
        max(1, r.size.height)
    }

    val digitCellWidthDp = with(density) { digitCellWidthPx.toDp() }
    val cellHeightDp = with(density) { cellHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (ch in text) {
                val width = if (ch.isDigit()) {
                    digitCellWidthDp
                } else {
                    measuredCharWidthDp(ch, measurer, style, strokePx)
                }

                Box(
                    modifier = Modifier
                        .width(width)
                        .height(cellHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedTextLayout(
                        text = ch.toString(),
                        modifier = Modifier,
                        style = style.copy(textAlign = TextAlign.Center),
                        shadow = shadow,
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidth = strokeWidth,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        verticalAlign = VerticalAlign.Center,
                        opticalCentering = opticalCentering,
                    )
                }
            }
        }
    }
}