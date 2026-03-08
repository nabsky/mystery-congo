package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerAdminDevicesRoutes(
    repository: AdminDevicesRepository
) {
    get("/admin/devices") {
        call.respond(repository.getOnlineSummary())
    }
}