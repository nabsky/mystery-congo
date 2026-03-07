package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object SystemStateTable : Table("system_state") {
    val id = long("id").autoIncrement()
    val systemMode = varchar("system_mode", 32)
    val stateVersion = long("state_version")
    val lastEventId = long("last_event_id")
    val pendingWinId = long("pending_win_id").nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

