package com.zorindisplays.display.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: MoneyFormat = MoneyFormat(),
    fillColor: Color = style.color,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 3.dp,
    textAlign: TextAlign = TextAlign.Start,
    verticalAlign: VerticalAlign = VerticalAlign.Top,
    opticalCentering: Boolean = false,
    roundingMode: RoundingMode = RoundingMode.HALF_UP,
) {
    val minor = remember(amount, format.fractionDigits, roundingMode) {
        amount.toMinorUnits(
            fractionDigits = format.fractionDigits,
            rounding = roundingMode
        )
    }

    AmountText(
        amountMinor = minor,
        modifier = modifier,
        style = style,
        format = format,
        fillColor = fillColor,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        textAlign = textAlign,
        verticalAlign = verticalAlign,
        opticalCentering = opticalCentering
    )
}

@Composable
fun AmountText(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle,
    format: MoneyFormat = MoneyFormat(),
    fillColor: Color = style.color,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 3.dp,
    textAlign: TextAlign = TextAlign.Start,
    verticalAlign: VerticalAlign = VerticalAlign.Top,
    opticalCentering: Boolean = false,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val text = formatMoneyFromMinor(amountMinor, format)

    OutlinedTextLayout(
        text = text,
        modifier = modifier,
        style = style,
        fillColor = fillColor,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = false,
        textAlign = textAlign,
        verticalAlign = verticalAlign,
        opticalCentering = opticalCentering,
    )
}

enum class CurrencyPosition { Prefix, Suffix }

data class MoneyFormat(
    val currency: String = "€",
    val currencyPosition: CurrencyPosition = CurrencyPosition.Prefix,
    val thousandsSeparator: Char = ' ',
    val decimalSeparator: Char = '.',
    val showCents: Boolean = true,
    val fractionDigits: Int = 2,
    val spaceBetweenCurrencyAndAmount: Boolean = true,
)

private fun formatMoneyFromMinor(
    minor: Long,
    format: MoneyFormat
): String {
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
    // raw — только цифры
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

private fun Double.toMinorUnits(
    fractionDigits: Int = 2,
    rounding: RoundingMode = RoundingMode.HALF_UP
): Long {
    val factor = BigDecimal.TEN.pow(fractionDigits)
    return BigDecimal.valueOf(this)
        .multiply(factor)
        .setScale(0, rounding)
        .longValueExact()
}