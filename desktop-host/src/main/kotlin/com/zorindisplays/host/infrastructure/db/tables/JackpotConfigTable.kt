package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object JackpotConfigTable : Table("jackpot_config") {
    val jackpotId = varchar("jackpot_id", 16)
    val resetAmount = long("reset_amount")
    val contributionPerBet = long("contribution_per_bet")
    val hitFrequencyGames = integer("hit_frequency_games")
    val priorityOrder = integer("priority_order")
    val enabled = bool("enabled")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(jackpotId)
}

