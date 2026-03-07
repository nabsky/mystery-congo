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
        call.respond(adminHistoryRepository.getBetBatches(limit))
    }

    get("/admin/jackpot-hits") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        call.respond(adminHistoryRepository.getJackpotHits(limit))
    }

    get("/admin/pending-wins") {
        call.respond(adminHistoryRepository.getPendingWins())
    }
}