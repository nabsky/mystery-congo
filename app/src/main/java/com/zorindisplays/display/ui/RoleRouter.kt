package com.zorindisplays.display.ui

import androidx.compose.runtime.*
import com.zorindisplays.display.model.DevicePrefs
import com.zorindisplays.display.model.DeviceRole
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import com.zorindisplays.display.ui.screens.MainScreen
import com.zorindisplays.display.model.MainViewModel
import com.zorindisplays.display.model.DeviceConfig
import com.zorindisplays.display.model.DataSourceProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import com.zorindisplays.display.model.DeviceRole as ModelDeviceRole

@Composable
fun RoleRouter() {
    val context = LocalContext.current
    // Use a stable scope tied to this composable's lifecycle
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    DisposableEffect(Unit) {
        onDispose { scope.cancel() }
    }

    val prefs = remember { DevicePrefs(context) }

    val role by prefs.roleFlow.collectAsState(initial = ModelDeviceRole.UNSET)
    val savedHostUrl by prefs.hostAddressFlow.collectAsState(initial = "http://192.168.1.100:8080")


    var showDialog by remember { mutableStateOf(false) }

    // Show dialog if role is unset or we want to change it (could add a hidden trigger later, but for now just on unset)
    LaunchedEffect(role) {
        if (role == ModelDeviceRole.UNSET) {
            showDialog = true
        } else {
            showDialog = false
        }
    }

    if (showDialog) {
        RolePickerDialog(
            currentRole = role,
            initialHostUrl = savedHostUrl,
            onRoleSelected = { selected ->
                scope.launch {
                    prefs.setRole(selected)
                }
            },
            onHostUrlChanged = { newUrl ->
                scope.launch {
                    prefs.setHostAddress(newUrl)
                }
            },
            onDismiss = { /* Forced choice? or strictly on UNSET */ }
        )
    }

    if (role != ModelDeviceRole.UNSET) {
        // Create Config
        val configRole = when(role) {
             ModelDeviceRole.DEMO -> ModelDeviceRole.DEMO
             ModelDeviceRole.HOST -> ModelDeviceRole.HOST
             ModelDeviceRole.TABLE -> ModelDeviceRole.TABLE
             else -> ModelDeviceRole.DEMO
        }
        val config = DeviceConfig(
            role = configRole,
            hostUrl = savedHostUrl
        )

        // Create DataSource
        // We use a key to recreate the ViewModel if config changes
        key(role, savedHostUrl) {

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val ds = DataSourceProvider(context.applicationContext, scope, config).create()
                    return MainViewModel(ds) as T
                }
            }

            // Re-keying ViewModel ensures we get a fresh instance
            val vm: MainViewModel = viewModel(key = "${role.name}|${savedHostUrl}", factory = factory)
            MainScreen(
                viewModel = vm,
                onResetRole = {
                    scope.launch {
                        prefs.setRole(ModelDeviceRole.UNSET)
                    }
                }
            )
        }
    }
}
