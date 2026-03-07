package com.zorindisplays.host.admin

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerAdminRoutes(adminHistoryRepository: AdminHistoryRepository) {

    get("/admin/health") {
        call.respond(mapOf("ok" to true))
    }

    get("/admin/bet-batches") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        val result = call.request.queryParameters["result"]
        val beforeId = call.request.queryParameters["beforeId"]?.toLongOrNull()

        call.respond(
            adminHistoryRepository.getBetBatches(
                limit = limit,
                tableId = tableId,
                result = result,
                beforeId = beforeId
            )
        )
    }

    get("/admin/jackpot-hits") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        val jackpotId = call.request.queryParameters["jackpotId"]
        val status = call.request.queryParameters["status"]
        val beforeId = call.request.queryParameters["beforeId"]?.toLongOrNull()

        call.respond(
            adminHistoryRepository.getJackpotHits(
                limit = limit,
                tableId = tableId,
                jackpotId = jackpotId,
                status = status,
                beforeId = beforeId
            )
        )
    }

    get("/admin/pending-wins") {
        val tableId = call.request.queryParameters["tableId"]?.toIntOrNull()
        val dealerConfirmed = call.request.queryParameters["dealerConfirmed"]?.toBooleanStrictOrNull()

        call.respond(
            adminHistoryRepository.getPendingWins(
                tableId = tableId,
                dealerConfirmed = dealerConfirmed
            )
        )
    }
}