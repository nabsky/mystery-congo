package com.zorindisplays.mystery.host.net

import com.zorindisplays.mystery.host.HostDataSource
import com.zorindisplays.mystery.host.HostRepository
import com.zorindisplays.mystery.host.net.dto.ConfirmPayoutRequest
import com.zorindisplays.mystery.host.net.dto.ConfirmRequest
import com.zorindisplays.mystery.host.net.dto.SelectPayoutBoxRequest
import com.zorindisplays.mystery.host.net.dto.SyncEvent
import com.zorindisplays.mystery.host.net.dto.SyncResponse
import com.zorindisplays.mystery.host.net.dto.ToggleRequest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineExceptionHandler

class HostHttpServer(
    private val hostDataSource: HostDataSource,
    private val hostRepository: HostRepository,
    private val scope: CoroutineScope,
    private val port: Int = 8080
) {
    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) return // Защита от двойного запуска

        val handler = CoroutineExceptionHandler { _, exception ->
             println("HostHttpServer: Error in server coroutine: $exception")
             exception.printStackTrace()
             server = null
        }

        scope.launch(Dispatchers.IO + handler) {
            try {
                val s = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    routing {
                        // ...
                        post("/input/toggle") {
                            val req = call.receive<ToggleRequest>()
                            hostDataSource.toggleBox(req.tableId, req.boxId)
                            call.respond(mapOf("ok" to true))
                        }
                        post("/input/confirm") {
                            val req = call.receive<ConfirmRequest>()
                            hostDataSource.confirmBets(req.tableId)
                            call.respond(mapOf("ok" to true))
                        }
                        post("/input/payout/selectBox") {
                            val req = call.receive<SelectPayoutBoxRequest>()
                            hostDataSource.selectPayoutBox(req.tableId, req.boxId)
                            call.respond(mapOf("ok" to true))
                        }
                        post("/input/payout/confirm") {
                            val req = call.receive<ConfirmPayoutRequest>()
                            hostDataSource.confirmPayout(req.tableId)
                            call.respond(mapOf("ok" to true))
                        }
                        get("/sync") {
                            withContext(Dispatchers.IO) {
                                hostRepository.ensureInitialized()
                                val afterEventId =
                                    call.request.queryParameters["afterEventId"]?.toLongOrNull() ?: 0L
                                val global = hostRepository.getGlobalState()
                                val stateVersion = global?.stateVersion ?: 0L
                                val lastEventId = global?.lastEventId ?: 0L
                                val events = hostRepository.getEventsAfter(afterEventId)
                                call.respond(
                                    SyncResponse(
                                        stateVersion = stateVersion,
                                        lastEventId = lastEventId,
                                        events = events.map {
                                            SyncEvent(
                                                eventId = it.eventId,
                                                ts = it.ts,
                                                type = it.type,
                                                payloadJson = it.payloadJson
                                            )
                                        }
                                    )
                                )
                            }
                        }
                        get("/health") {
                            withContext(Dispatchers.IO) {
                                hostRepository.ensureInitialized()
                                val global = hostRepository.getGlobalState()
                                val stateVersion = global?.stateVersion ?: 0L
                                val lastEventId = global?.lastEventId ?: 0L
                                call.respond(
                                    mapOf(
                                        "ok" to true,
                                        "stateVersion" to stateVersion,
                                        "lastEventId" to lastEventId
                                    )
                                )
                            }
                        }
                        get("/snapshot") {
                            val snapshot = hostRepository.snapshot()
                            call.respond(snapshot)
                        }
                    }
                }
                server = s
                s.start(wait = true)
            } catch (e: Exception) {
                e.printStackTrace()
                server = null
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}