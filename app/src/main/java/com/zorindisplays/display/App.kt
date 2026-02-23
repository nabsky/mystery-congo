package com.zorindisplays.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zorindisplays.display.ui.screens.MainScreen
import com.zorindisplays.display.ui.theme.DefaultBackground

@Composable
fun App() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DefaultBackground)
    ) {
        MainScreen()
    }
}
