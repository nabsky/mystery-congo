package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object SyncEventTable : Table("sync_event") {
    val eventId = long("event_id").autoIncrement()
    val createdAt = long("created_at")
    val type = varchar("type", 32)
    val payloadJson = text("payload_json")
    val stateVersion = long("state_version")
    init {
        index(false, createdAt)
        index(false, type)
    }
    override val primaryKey = PrimaryKey(eventId)
}

