package com.zorindisplays.host.api

import com.zorindisplays.host.application.service.QueryService
import com.zorindisplays.host.application.query.GetSnapshotQuery
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.application.call

fun Route.registerSnapshotRoutes(queryService: QueryService) {
    get("/snapshot") {
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        val query = GetSnapshotQuery(tableId)
        call.respond(queryService.getSnapshot(query))
    }
}
