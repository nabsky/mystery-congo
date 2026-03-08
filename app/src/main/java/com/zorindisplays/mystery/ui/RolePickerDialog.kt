package com.zorindisplays.mystery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zorindisplays.mystery.model.DeviceRole
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun RolePickerDialog(
    currentRole: DeviceRole,
    initialHostUrl: String,
    initialTableId: Int,
    onRoleSelected: (DeviceRole) -> Unit,
    onHostUrlChanged: (String) -> Unit,
    onTableIdChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hostUrl by remember { mutableStateOf(initialHostUrl) }
    var tableIdStr by remember { mutableStateOf(initialTableId.toString()) }

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

                androidx.compose.material3.OutlinedTextField(
                    value = tableIdStr,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                           tableIdStr = it
                           onTableIdChanged(it.toIntOrNull() ?: 0)
                        }
                    },
                    label = { Text("Table ID (0-7)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Button(
                    onClick = { onRoleSelected(DeviceRole.DEMO); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("DEMO (Emulator)") }

                Button(
                    onClick = {
                        onRoleSelected(DeviceRole.DISPLAY)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("DISPLAY (Viewer)") }

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
