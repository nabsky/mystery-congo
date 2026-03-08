package com.zorindisplays.display.model

import android.content.Context
import com.zorindisplays.display.emulator.Emulator
import com.zorindisplays.display.host.HostDataSource
import com.zorindisplays.display.host.HostRepository
import com.zorindisplays.display.host.net.HostHttpServer
import com.zorindisplays.display.table.TableDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

            DeviceRole.DISPLAY -> {

                var url = config.hostUrl
                if (!url.startsWith("http")) {
                    url = "http://$url"
                }

                if (!url.substringAfter("://").contains(":")) {
                    url = "$url:8080"
                }

                // DISPLAY просто подключается как tableId = -1
                TableDataSource(
                    baseUrl = url,
                    tableId = -1,
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
                    scope = scope
                )
            }

            else -> throw IllegalStateException("Role must be set before creating data source")
        }
    }

    private class HostDataSourceWrapper(
        private val delegate: HostDataSource,
        private val server: HostHttpServer
    ) : JackpotDataSource {
        override val state: StateFlow<DemoState> get() = delegate.state
        override val events: SharedFlow<DemoEvent> get() = delegate.events

        override fun start(scope: CoroutineScope) = delegate.start(scope)
        override suspend fun stop() {
            server.stop()
            delegate.stop()
        }

        override suspend fun toggleBox(tableId: Int, boxId: Int) = delegate.toggleBox(tableId, boxId)
        override suspend fun confirmBets(tableId: Int) = delegate.confirmBets(tableId)
        override suspend fun selectPayoutBox(tableId: Int, boxId: Int) = delegate.selectPayoutBox(tableId, boxId)
        override suspend fun confirmPayout(tableId: Int) = delegate.confirmPayout(tableId)
    }
}