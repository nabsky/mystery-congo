package com.zorindisplays.host.api

import com.zorindisplays.host.application.query.GetSyncQuery
import com.zorindisplays.host.application.service.QueryService
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerSyncRoutes(queryService: QueryService) {
    get("/sync") {
        val afterEventId = call.request.queryParameters["afterEventId"]?.toLongOrNull() ?: 0L
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()

        call.respond(
            queryService.getSync(
                GetSyncQuery(
                    afterEventId = afterEventId,
                    tableId = tableId
                )
            )
        )
    }
}