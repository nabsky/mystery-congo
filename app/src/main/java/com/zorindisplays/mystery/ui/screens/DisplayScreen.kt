package com.zorindisplays.mystery.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zorindisplays.mystery.model.MainViewModel

@Composable
fun DisplayScreen(
    viewModel: MainViewModel,
    onResetRole: () -> Unit
) {
    MainSceneScreen(
        viewModel = viewModel,
        tableId = -1,
        inputModifier = Modifier,
        onResetRole = onResetRole
    )
}