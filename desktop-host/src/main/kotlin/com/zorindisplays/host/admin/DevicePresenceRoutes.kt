package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.registerDevicePresenceRoutes(
    repository: AdminDevicesRepository
) {
    post("/device/heartbeat") {
        val request = call.receive<DeviceHeartbeatRequest>()
        call.respond(repository.heartbeat(request))
    }
}