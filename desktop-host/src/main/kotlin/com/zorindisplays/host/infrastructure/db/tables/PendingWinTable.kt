package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object PendingWinTable : Table("pending_win") {
    val id = long("id").autoIncrement()
    val jackpotId = varchar("jackpot_id", 16)
    val tableId = integer("table_id")
    val selectedBoxId = integer("selected_box_id").nullable()
    val winAmount = long("win_amount")
    val status = varchar("status", 32)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

