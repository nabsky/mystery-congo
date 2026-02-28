package com.zorindisplays.display.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JackpotWinOverlay(
    modifier: Modifier = Modifier,
    bgColor: Color,
    title: String,
    amountText: String,
    subtitle: String,
    fontFamily: FontFamily? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(bgColor)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontFamily = fontFamily,
                    fontSize = 26.sp,
                    letterSpacing = 4.sp,
                )
            )

            Spacer(Modifier.height(18.dp))

            BasicText(
                text = amountText,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontSize = 84.sp,
                    letterSpacing = 1.sp,
                )
            )

            Spacer(Modifier.height(16.dp))

            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.86f),
                    fontFamily = fontFamily,
                    fontSize = 20.sp,
                    letterSpacing = 1.6.sp,
                )
            )
        }
    }
}