package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object TableActiveBoxTable : Table("table_active_box") {
    val tableId = integer("table_id")
    val boxId = integer("box_id")
    val selectedAt = long("selected_at")
    override val primaryKey = PrimaryKey(tableId, boxId)
}

