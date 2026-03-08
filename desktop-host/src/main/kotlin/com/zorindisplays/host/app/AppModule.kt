package com.zorindisplays.host.app

import com.zorindisplays.host.admin.AdminDashboardRepository
import com.zorindisplays.host.admin.AdminHistoryRepository
import com.zorindisplays.host.admin.AdminSettingsRepository
import com.zorindisplays.host.admin.AdminSystemRepository
import com.zorindisplays.host.admin.registerAdminRoutes
import com.zorindisplays.host.admin.registerAdminSettingsRoutes
import com.zorindisplays.host.admin.registerAdminDashboardRoutes
import com.zorindisplays.host.admin.registerAdminSystemRoutes
import com.zorindisplays.host.api.registerAdminWsRoutes
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
import io.ktor.server.websocket.*
import java.time.Duration
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.Authentication

fun Application.appModule(
    config: AppConfig,
    queryService: QueryService,
    commandService: CommandService,
    adminHistoryRepository: AdminHistoryRepository,
    adminSettingsRepository: AdminSettingsRepository,
    adminDashboardRepository: AdminDashboardRepository,
    adminSystemRepository: AdminSystemRepository,
) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(StatusPages) { }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(30)
    }
    install(Authentication) {
        basic("admin-auth") {
            realm = "Mystery Admin"
            validate { credentials ->
                if (
                    credentials.name == config.adminUsername &&
                    credentials.password == config.adminPassword
                ) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    routing {
        registerHealthRoutes(queryService)
        registerSnapshotRoutes(queryService)
        registerSyncRoutes(queryService)
        registerInputRoutes(commandService)

        authenticate("admin-auth") {
            registerAdminRoutes(adminHistoryRepository)
            registerAdminSettingsRoutes(adminSettingsRepository)
            registerAdminDashboardRoutes(adminDashboardRepository)
            registerAdminSystemRoutes(adminSystemRepository)
            registerAdminWsRoutes()

            get("/mystery") {
                call.respondRedirect("/admin/ui/index.html")
            }

            staticResources("/admin/ui", "static/admin")
        }
    }
}