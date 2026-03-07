package com.zorindisplays.host.admin

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

object AdminRoutes {
    fun register(app: Application) {
        app.routing {
            get("/admin/health") {
                call.respond(mapOf("ok" to true))
            }
        }
    }
}