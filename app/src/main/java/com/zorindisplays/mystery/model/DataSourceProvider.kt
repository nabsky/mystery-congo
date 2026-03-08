package com.zorindisplays.mystery.model

import com.zorindisplays.mystery.display.DisplayDataSource
import com.zorindisplays.mystery.emulator.Emulator
import com.zorindisplays.mystery.emulator.EmulatorDataSource
import com.zorindisplays.mystery.table.TableDataSource
import kotlinx.coroutines.CoroutineScope

class DataSourceProvider(
    private val scope: CoroutineScope,
    private val config: DeviceConfig
) {
    fun create(): JackpotStateDataSource {
        return when(config.role) {

            DeviceRole.DEMO -> EmulatorDataSource(
                emulator = Emulator(
                    scope = scope,
                    tableCount = 8,
                    boxesPerTable = 9
                )
            )

            DeviceRole.DISPLAY -> {

                var url = config.hostUrl

                if (!url.startsWith("http")) {
                    url = "http://$url"
                }

                if (!url.substringAfter("://").contains(":")) {
                    url = "$url:8080"
                }

                DisplayDataSource(
                    baseUrl = url,
                    deviceId = config.deviceId,
                    scope = scope
                )
            }

            DeviceRole.TABLE -> {

                var url = config.hostUrl
                if (!url.startsWith("http")) {
                    url = "http://$url"
                }

                if (!url.substringAfter("://").contains(":")) {
                    url = "$url:8080"
                }

                TableDataSource(
                    baseUrl = url,
                    tableId = config.tableId,
                    deviceId = config.deviceId,
                    scope = scope
                )
            }

            else -> throw IllegalStateException("Role must be set before creating data source")
        }
    }
}