package com.zorindisplays.display

import android.content.Context
import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.model.JackpotDataSource
import com.zorindisplays.display.model.EmulatorDataSource
import com.zorindisplays.display.host.HostDataSource
import com.zorindisplays.display.host.HostRepository
import com.zorindisplays.display.host.net.HostHttpServer

import com.zorindisplays.display.table.TableDataSource
import kotlinx.coroutines.CoroutineScope

class DataSourceProvider(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: DeviceConfig
) {
    fun create(): JackpotDataSource {
        return when(config.role) {
            DeviceRole.DEMO -> EmulatorDataSource(
                emulator = Emulator(
                    scope = scope,
                    tableCount = 8,
                    boxesPerTable = 9
                )
            )
            DeviceRole.HOST -> {
                val repo = HostRepository(context)
                val ds = HostDataSource(repo, scope)
                val server = HostHttpServer(
                    hostDataSource = ds,
                    hostRepository = repo,
                    scope = scope
                )
                server.start()
                ds
            }
            DeviceRole.TABLE ->
                TableDataSource(
                    baseUrl = config.hostUrl,
                    scope = scope
                )
        }
    }
}

