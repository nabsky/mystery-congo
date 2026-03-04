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
    onRoleSelected: (DeviceRole) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите роль устройства") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onRoleSelected(DeviceRole.DEMO); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("DEMO") }
                Button(
                    onClick = {
                        // HOST/TABLE пока не реализовано
                        onRoleSelected(DeviceRole.DEMO)
                        onDismiss()
                        // Можно показать Snackbar/Toast/Alert
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("HOST (not implemented)") }
                Button(
                    onClick = {
                        onRoleSelected(DeviceRole.DEMO)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("TABLE (not implemented)") }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
