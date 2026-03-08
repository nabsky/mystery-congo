package com.zorindisplays.mystery.model

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.devicePrefsDataStore by preferencesDataStore(name = "device_prefs")

const val DEFAULT_HOST_ADDRESS = "http://192.168.1.100:8080"

data class DeviceConfig(
    val role: DeviceRole = DeviceRole.UNSET,
    val hostUrl: String = "",
    val tableId: Int = 0,
    val deviceId: String = ""
)

object DevicePrefsKeys {
    val role = stringPreferencesKey("role")
    val deviceId = stringPreferencesKey("deviceId")
    val hostAddress = stringPreferencesKey("hostAddress")
    val port = intPreferencesKey("port")
    val adminToken = stringPreferencesKey("adminToken")
    val tableId = intPreferencesKey("tableId")
}

class DevicePrefs(private val context: Context) {
    val roleFlow: Flow<DeviceRole> = context.devicePrefsDataStore.data.map { prefs ->
        DeviceRole.valueOf(prefs[DevicePrefsKeys.role] ?: DeviceRole.UNSET.name)
    }

    val hostAddressFlow: Flow<String> = context.devicePrefsDataStore.data.map { prefs ->
        prefs[DevicePrefsKeys.hostAddress] ?: DEFAULT_HOST_ADDRESS
    }

    val tableIdFlow: Flow<Int> = context.devicePrefsDataStore.data.map { prefs ->
        (prefs[DevicePrefsKeys.tableId] ?: 0).coerceIn(0, 7)
    }

    val deviceIdFlow: Flow<String> = context.devicePrefsDataStore.data.map { prefs ->
        prefs[DevicePrefsKeys.deviceId] ?: ""
    }

    suspend fun setRole(role: DeviceRole) {
        context.devicePrefsDataStore.edit { prefs ->
            prefs[DevicePrefsKeys.role] = role.name
        }
    }

    suspend fun setHostAddress(address: String) {
        context.devicePrefsDataStore.edit { prefs ->
            prefs[DevicePrefsKeys.hostAddress] = address
        }
    }

    suspend fun setTableId(id: Int) {
        context.devicePrefsDataStore.edit { prefs ->
            prefs[DevicePrefsKeys.tableId] = id.coerceIn(0, 7)
        }
    }


    suspend fun ensureDeviceId(): String {
        var id = ""
        context.devicePrefsDataStore.edit { prefs ->
            id = prefs[DevicePrefsKeys.deviceId] ?: UUID.randomUUID().toString().also {
                prefs[DevicePrefsKeys.deviceId] = it
            }
        }
        return id
    }

    suspend fun saveConfig(
        role: DeviceRole,
        hostAddress: String,
        tableId: Int
    ) {
        context.devicePrefsDataStore.edit { prefs ->
            prefs[DevicePrefsKeys.role] = role.name
            prefs[DevicePrefsKeys.hostAddress] = hostAddress.trim()
            prefs[DevicePrefsKeys.tableId] = tableId.coerceIn(0, 7)
        }
    }
}
