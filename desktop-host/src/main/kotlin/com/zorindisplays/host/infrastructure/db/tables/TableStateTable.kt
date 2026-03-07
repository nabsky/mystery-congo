package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object TableStateTable : Table("table_state") {
    val tableId = integer("table_id")
    val lastSeenAt = long("last_seen_at").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(tableId)
}

