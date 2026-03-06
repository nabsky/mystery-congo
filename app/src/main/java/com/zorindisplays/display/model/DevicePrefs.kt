package com.zorindisplays.display.model

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
        prefs[DevicePrefsKeys.hostAddress] ?: "http://192.168.1.100:8080"
    }

    val tableIdFlow: Flow<Int> = context.devicePrefsDataStore.data.map { prefs ->
       prefs[DevicePrefsKeys.tableId] ?: 0
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
            prefs[DevicePrefsKeys.tableId] = id
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
}
