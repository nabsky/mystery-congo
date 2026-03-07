package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.registerAdminSettingsRoutes(
    adminSettingsRepository: AdminSettingsRepository
) {
    get("/admin/settings/jackpots") {
        call.respond(adminSettingsRepository.getJackpotSettings())
    }

    post("/admin/settings/jackpots/{jackpotId}") {
        val jackpotId = call.parameters["jackpotId"]
            ?: error("Missing jackpotId")

        val request = call.receive<UpdateAdminJackpotSettingsRequest>()
        adminSettingsRepository.updateJackpotSettings(jackpotId, request)
        call.respond(mapOf("ok" to true))
    }
}