package com.zorindisplays.host.api

import com.zorindisplays.host.application.query.GetSnapshotQuery
import com.zorindisplays.host.application.service.QueryService
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerSnapshotRoutes(queryService: QueryService) {
    get("/snapshot") {
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        call.respond(queryService.getSnapshot(GetSnapshotQuery(tableId)))
    }
}