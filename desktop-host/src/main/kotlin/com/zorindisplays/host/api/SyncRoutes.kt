package com.zorindisplays.host.api

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import com.zorindisplays.host.application.service.QueryService
import com.zorindisplays.host.application.query.GetSyncQuery
import io.ktor.server.application.call

fun Route.registerSyncRoutes(queryService: QueryService) {
    get("/sync") {
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        val afterEventId = call.request.queryParameters["afterEventId"]?.toLongOrNull() ?: 0L
        val query = GetSyncQuery(afterEventId, tableId)
        call.respond(queryService.getSync(query))
    }
}
