package com.zorindisplays.display.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.zorindisplays.display.R

// Colors
val DefaultBackground = Color.Black
val PrimaryTextColor = Color.White

// Fonts
val MontserratBold = FontFamily(
    Font(R.font.montserrat_bold)
)
val MontserratBlackItalic = FontFamily(
    Font(R.font.montserrat_black_italic)
)

// Text styles
val DefaultTextStyle = TextStyle(
    fontFamily = MontserratBold,
    fontSize = 24.sp,
    color = PrimaryTextColor
)