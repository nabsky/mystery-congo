package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.registerAdminSystemRoutes(
    adminSystemRepository: AdminSystemRepository
) {
    post("/admin/system/refresh") {
        call.respond(adminSystemRepository.refresh())
    }

    post("/admin/system/clear-active-boxes") {
        call.respond(adminSystemRepository.clearActiveBoxes())
    }

    post("/admin/system/clear-recent-boxes") {
        call.respond(adminSystemRepository.clearRecentBoxes())
    }

    post("/admin/system/reset-pending-win") {
        call.respond(adminSystemRepository.resetPendingWin())
    }
}