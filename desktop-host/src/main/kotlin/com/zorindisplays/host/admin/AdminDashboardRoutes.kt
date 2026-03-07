package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerAdminDashboardRoutes(
    adminDashboardRepository: AdminDashboardRepository
) {
    get("/admin/dashboard") {
        call.respond(adminDashboardRepository.getDashboard())
    }
}