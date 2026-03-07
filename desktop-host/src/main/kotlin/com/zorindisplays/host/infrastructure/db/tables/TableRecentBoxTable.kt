package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object TableRecentBoxTable : Table("table_recent_box") {
    val tableId = integer("table_id")
    val boxId = integer("box_id")
    val confirmedAt = long("confirmed_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(tableId, boxId)
    init {
        index(true, expiresAt)
    }
}

