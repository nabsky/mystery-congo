package com.zorindisplays.host.admin

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AdminWsHub {
    private val mutex = Mutex()
    private val sessions = linkedSetOf<WebSocketSession>()

    suspend fun add(session: WebSocketSession) {
        mutex.withLock {
            sessions.add(session)
        }
    }

    suspend fun remove(session: WebSocketSession) {
        mutex.withLock {
            sessions.remove(session)
        }
    }

    suspend fun broadcast(text: String) {
        val snapshot = mutex.withLock { sessions.toList() }
        val dead = mutableListOf<WebSocketSession>()

        snapshot.forEach { session ->
            runCatching {
                session.send(Frame.Text(text))
            }.onFailure {
                dead += session
            }
        }

        if (dead.isNotEmpty()) {
            mutex.withLock {
                sessions.removeAll(dead.toSet())
            }
        }
    }
}