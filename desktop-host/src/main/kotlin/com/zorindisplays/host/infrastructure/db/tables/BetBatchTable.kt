package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object BetBatchTable : Table("bet_batch") {
    val id = long("id").autoIncrement()
    val tableId = integer("table_id")
    val confirmedAt = long("confirmed_at")
    val boxCount = integer("box_count")
    val result = varchar("result", 32).nullable()
    val winningJackpotId = varchar("winning_jackpot_id", 16).nullable()
    init {
        index(false, tableId)
        index(false, confirmedAt)
    }
    override val primaryKey = PrimaryKey(id)
}

