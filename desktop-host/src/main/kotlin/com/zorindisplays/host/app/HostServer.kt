package com.zorindisplays.host.app

import com.zorindisplays.host.application.service.CommandService
import com.zorindisplays.host.application.service.DefaultCommandService
import com.zorindisplays.host.application.service.DefaultQueryService
import com.zorindisplays.host.application.service.QueryService
import com.zorindisplays.host.infrastructure.db.DatabaseBootstrap
import com.zorindisplays.host.infrastructure.db.DatabaseFactory
import com.zorindisplays.host.infrastructure.db.tables.BetBatchItemTable
import com.zorindisplays.host.infrastructure.db.tables.BetBatchTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotConfigTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotHitTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SyncEventTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import com.zorindisplays.host.infrastructure.projection.SnapshotProjection
import com.zorindisplays.host.infrastructure.repository.ExposedJackpotRepository
import com.zorindisplays.host.infrastructure.repository.ExposedPendingWinRepository
import com.zorindisplays.host.infrastructure.repository.ExposedStateRepository
import com.zorindisplays.host.infrastructure.repository.ExposedSyncEventRepository
import com.zorindisplays.host.infrastructure.repository.ExposedTableSelectionRepository
import com.zorindisplays.host.infrastructure.repository.HostWriteRepository
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = AppConfig.fromEnv()

    DatabaseFactory.init(config)
    DatabaseFactory.createSchema(
        SystemStateTable,
        JackpotConfigTable,
        JackpotStateTable,
        TableStateTable,
        TableActiveBoxTable,
        TableRecentBoxTable,
        PendingWinTable,
        BetBatchTable,
        BetBatchItemTable,
        JackpotHitTable,
        SyncEventTable
    )
    DatabaseBootstrap.bootstrap(config)

    val stateRepository = ExposedStateRepository()
    val tableSelectionRepository = ExposedTableSelectionRepository()
    val syncEventRepository = ExposedSyncEventRepository()
    val jackpotRepository = ExposedJackpotRepository()
    val pendingWinRepository = ExposedPendingWinRepository()
    val hostWriteRepository = HostWriteRepository()

    val snapshotProjection = SnapshotProjection()

    val queryService: QueryService = DefaultQueryService(
        stateRepository = stateRepository,
        tableSelectionRepository = tableSelectionRepository,
        syncEventRepository = syncEventRepository,
        snapshotProjection = snapshotProjection
    )

    val commandService: CommandService = DefaultCommandService(
        config = config,
        stateRepository = stateRepository,
        tableSelectionRepository = tableSelectionRepository,
        syncEventRepository = syncEventRepository,
        jackpotRepository = jackpotRepository,
        pendingWinRepository = pendingWinRepository,
        hostWriteRepository = hostWriteRepository
    )

    embeddedServer(Netty, port = config.port, host = config.host) {
        appModule(queryService, commandService)
    }.start(wait = true)
}