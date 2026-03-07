package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object JackpotStateTable : Table("jackpot_state") {
    val jackpotId = varchar("jackpot_id", 16)
    val currentAmount = long("current_amount")
    val gamesSinceLastHit = integer("games_since_last_hit")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(jackpotId)
}

