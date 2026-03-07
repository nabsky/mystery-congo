package com.zorindisplays.host.app

import com.zorindisplays.host.admin.AdminHistoryRepository
import com.zorindisplays.host.admin.registerAdminRoutes
import com.zorindisplays.host.api.registerHealthRoutes
import com.zorindisplays.host.api.registerInputRoutes
import com.zorindisplays.host.api.registerSnapshotRoutes
import com.zorindisplays.host.api.registerSyncRoutes
import com.zorindisplays.host.application.service.CommandService
import com.zorindisplays.host.application.service.QueryService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get

fun Application.appModule(
    queryService: QueryService,
    commandService: CommandService,
    adminHistoryRepository: AdminHistoryRepository
) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(StatusPages) { }

    routing {
        registerHealthRoutes(queryService)
        registerSnapshotRoutes(queryService)
        registerSyncRoutes(queryService)
        registerInputRoutes(commandService)
        registerAdminRoutes(adminHistoryRepository)

        get("/mystery") {
            call.respondRedirect("/admin/ui/index.html")
        }

        staticResources("/admin/ui", "static/admin")
    }
}