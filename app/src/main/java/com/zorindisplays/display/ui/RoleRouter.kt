package com.zorindisplays.display.ui

import android.content.Context
import androidx.compose.runtime.*
import com.zorindisplays.display.model.DevicePrefs
import com.zorindisplays.display.model.DeviceRole
import androidx.compose.ui.platform.LocalContext
import com.zorindisplays.display.ui.screens.MainScreen

@Composable
fun RoleRouter() {
    val context = LocalContext.current
    val prefs = remember { DevicePrefs(context) }
    val role by prefs.roleFlow.collectAsState(initial = DeviceRole.UNSET)
    var showDialog by remember { mutableStateOf(role == DeviceRole.UNSET) }
    var pendingRole by remember { mutableStateOf<DeviceRole?>(null) }

    if (showDialog) {
        RolePickerDialog(
            currentRole = role,
            onRoleSelected = { selected ->
                pendingRole = selected
            },
            onDismiss = { showDialog = false }
        )
    }

    LaunchedEffect(pendingRole) {
        if (pendingRole != null) {
            prefs.setRole(DeviceRole.DEMO)
            pendingRole = null
        }
    }

    if (role == DeviceRole.DEMO) {
        MainScreen()
    } else {
        // HOST/TABLE пока не реализовано, показываем DEMO
        MainScreen()
    }
}
