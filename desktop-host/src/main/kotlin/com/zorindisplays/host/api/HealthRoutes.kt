package com.zorindisplays.host.api

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import com.zorindisplays.host.application.service.QueryService
import io.ktor.server.application.call

fun Route.registerHealthRoutes(queryService: QueryService) {
    get("/health") {
        call.respond(queryService.getHealth())
    }
}
