package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.registerAdminDevicesRoutes(
    repository: AdminDevicesRepository
) {
    post("/device/heartbeat") {
        val request = call.receive<DeviceHeartbeatRequest>()
        call.respond(repository.heartbeat(request))
    }

    get("/admin/devices") {
        call.respond(repository.getOnlineSummary())
    }
}