package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.registerAdminServerSettingsRoutes(
    repository: AdminServerSettingsRepository
) {
    get("/admin/settings/server") {
        call.respond(repository.getSettings())
    }

    post("/admin/settings/server") {
        val request = call.receive<UpdateServerSettingsRequest>()
        call.respond(repository.updateSettings(request))
    }
}