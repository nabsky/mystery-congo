package com.zorindisplays.mystery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zorindisplays.mystery.model.DeviceRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolePickerDialog(
    currentRole: DeviceRole,
    initialHostUrl: String,
    initialTableId: Int,
    onSave: (DeviceRole, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRole by remember { mutableStateOf(if (currentRole == DeviceRole.UNSET) DeviceRole.DEMO else currentRole) }
    var hostUrl by remember { mutableStateOf(initialHostUrl) }

    // UI показывает 1..8, внутрь сохраняем 0..7
    var selectedTableNumber by remember {
        mutableIntStateOf((initialTableId.coerceIn(0, 7)) + 1)
    }

    var tableMenuExpanded by remember { mutableStateOf(false) }

    val requiresHost = selectedRole == DeviceRole.DISPLAY || selectedRole == DeviceRole.TABLE
    val requiresTable = selectedRole == DeviceRole.TABLE

    fun save() {
        val finalTableId = if (selectedRole == DeviceRole.TABLE) {
            selectedTableNumber - 1
        } else {
            initialTableId.coerceIn(0, 7)
        }

        onSave(
            selectedRole,
            hostUrl.trim(),
            finalTableId
        )
        onDismiss()
    }

    val isSaveEnabled =
        when (selectedRole) {
            DeviceRole.DEMO -> true
            DeviceRole.DISPLAY, DeviceRole.TABLE -> hostUrl.trim().isNotEmpty()
            else -> false
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select device role") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Role")

                RoleOptionRow(
                    selected = selectedRole == DeviceRole.DEMO,
                    title = "Demo",
                    subtitle = "Local simulation",
                    onClick = { selectedRole = DeviceRole.DEMO }
                )

                RoleOptionRow(
                    selected = selectedRole == DeviceRole.DISPLAY,
                    title = "Display",
                    subtitle = "Passive viewer",
                    onClick = { selectedRole = DeviceRole.DISPLAY }
                )

                RoleOptionRow(
                    selected = selectedRole == DeviceRole.TABLE,
                    title = "Table",
                    subtitle = "Accepts bets for one table",
                    onClick = { selectedRole = DeviceRole.TABLE }
                )

                if (requiresHost) {
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = hostUrl,
                        onValueChange = { hostUrl = it },
                        label = { Text("Host URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (requiresTable) {
                    ExposedDropdownMenuBox(
                        expanded = tableMenuExpanded,
                        onExpandedChange = { tableMenuExpanded = !tableMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = "Table $selectedTableNumber",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Table number") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = tableMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = tableMenuExpanded,
                            onDismissRequest = { tableMenuExpanded = false }
                        ) {
                            (1..8).forEach { tableNumber ->
                                DropdownMenuItem(
                                    text = { Text("Table $tableNumber") },
                                    onClick = {
                                        selectedTableNumber = tableNumber
                                        tableMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::save,
                enabled = isSaveEnabled
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoleOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Column(
            modifier = Modifier.padding(start = 6.dp)
        ) {
            Text(text = title)
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}