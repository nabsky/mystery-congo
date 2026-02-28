package com.zorindisplays.display.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBrandBar(
    modifier: Modifier = Modifier,
    text: String = "ALL JACKPOTS IN CFA",
    fontFamily: FontFamily? = null,
) {

    val backdrop = Modifier.drawWithCache {
        val gradient = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.40f),
                Color.Black.copy(alpha = 0.18f),
                Color.Transparent
            )
        )

        onDrawBehind {
            drawRect(gradient)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .then(backdrop)
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.CenterStart
    ) {

        BasicText(
            text = text,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = fontFamily,
                fontSize = 13.sp,
                letterSpacing = 2.0.sp
            )
        )
    }
}