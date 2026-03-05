package com.zorindisplays.display.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zorindisplays.display.model.DeviceRole

@Composable
fun RolePickerDialog(
    currentRole: DeviceRole,
    initialHostUrl: String,
    onRoleSelected: (DeviceRole) -> Unit,
    onHostUrlChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hostUrl by remember { mutableStateOf(initialHostUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите роль устройства") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Input for Host URL (mostly for TABLE)
                androidx.compose.material3.OutlinedTextField(
                    value = hostUrl,
                    onValueChange = {
                       hostUrl = it
                       onHostUrlChanged(it)
                    },
                    label = { Text("Host URL (for TABLE)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Button(
                    onClick = { onRoleSelected(DeviceRole.DEMO); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("DEMO (Emulator)") }

                Button(
                    onClick = {
                        onRoleSelected(DeviceRole.HOST)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("HOST (Server)") }

                Button(
                    onClick = {
                        onRoleSelected(DeviceRole.TABLE)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("TABLE (Client)") }
            }
        },
        confirmButton = {
            // No cancel, must pick role? Or allow dismiss if role already set
            if (currentRole != DeviceRole.UNSET) {
                Button(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}
