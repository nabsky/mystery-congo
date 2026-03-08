package com.zorindisplays.mystery.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.zorindisplays.mystery.model.JackpotState
import com.zorindisplays.mystery.model.JackpotTableControlDataSource
import com.zorindisplays.mystery.model.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun TableScreen(
    viewModel: MainViewModel,
    tableId: Int,
    onResetRole: () -> Unit
) {
    val uiScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val dataSource = viewModel.dataSource
    val tableControlDataSource = dataSource as? JackpotTableControlDataSource
    val demo by dataSource.state.collectAsState()

    val isPayoutPendingForThisTable =
        demo.systemMode == JackpotState.SystemMode.PAYOUT_PENDING &&
                demo.pendingWin?.tableId == tableId

    val inputModifier = Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || tableId < 0 || tableControlDataSource == null) {
                return@onKeyEvent false
            }

            val boxIdx = when (event.key) {
                Key.One, Key.NumPad1 -> 0
                Key.Two, Key.NumPad2 -> 1
                Key.Three, Key.NumPad3 -> 2
                Key.Four, Key.NumPad4 -> 3
                Key.Five, Key.NumPad5 -> 4
                Key.Six, Key.NumPad6 -> 5
                Key.Seven, Key.NumPad7 -> 6
                Key.Eight, Key.NumPad8 -> 7
                Key.Nine, Key.NumPad9 -> 8
                else -> -1
            }

            if (isPayoutPendingForThisTable) {
                if (boxIdx >= 0) {
                    uiScope.launch {
                        tableControlDataSource.selectPayoutBox(tableId, boxIdx)
                    }
                    return@onKeyEvent true
                }

                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    uiScope.launch {
                        tableControlDataSource.confirmPayout(tableId)
                    }
                    return@onKeyEvent true
                }

                return@onKeyEvent false
            }

            if (boxIdx >= 0) {
                uiScope.launch {
                    tableControlDataSource.toggleBox(tableId, boxIdx)
                }
                return@onKeyEvent true
            }

            if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                uiScope.launch {
                    tableControlDataSource.confirmBets(tableId)
                }
                return@onKeyEvent true
            }

            false
        }

    MainSceneScreen(
        viewModel = viewModel,
        tableId = tableId,
        inputModifier = inputModifier,
        onResetRole = onResetRole
    )

    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }
}