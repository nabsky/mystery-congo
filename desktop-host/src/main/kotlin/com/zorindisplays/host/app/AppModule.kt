package com.zorindisplays.host.app

import io.ktor.server.application.Application
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import com.zorindisplays.host.application.service.QueryService
import com.zorindisplays.host.application.service.CommandService
import com.zorindisplays.host.api.registerHealthRoutes
import com.zorindisplays.host.api.registerSnapshotRoutes
import com.zorindisplays.host.api.registerSyncRoutes
import com.zorindisplays.host.api.registerInputRoutes
import io.ktor.server.application.install

fun Application.appModule(
    queryService: QueryService,
    commandService: CommandService
) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(StatusPages) { }
    routing {
        registerHealthRoutes(queryService)
        registerSnapshotRoutes(queryService)
        registerSyncRoutes(queryService)
        registerInputRoutes(commandService)
    }
}
