package com.zorindisplays.host.api

import com.zorindisplays.host.admin.AdminLiveMessage
import com.zorindisplays.host.admin.AdminWsHub
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.registerAdminWsRoutes() {
    webSocket("/admin/ws") {
        AdminWsHub.add(this)

        send(
            Frame.Text(
                Json.encodeToString(
                    AdminLiveMessage(
                        type = "connected",
                        message = "admin websocket connected"
                    )
                )
            )
        )

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "ping") {
                        send(
                            Frame.Text(
                                Json.encodeToString(
                                    AdminLiveMessage(
                                        type = "pong",
                                        message = "pong"
                                    )
                                )
                            )
                        )
                    }
                }
            }
        } finally {
            AdminWsHub.remove(this)
        }
    }
}