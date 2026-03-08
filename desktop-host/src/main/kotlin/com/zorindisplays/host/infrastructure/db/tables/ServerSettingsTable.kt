package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object ServerSettingsTable : Table("server_settings") {
    val id = long("id")
    val currencyCode = varchar("currency_code", 3)
    val baseBetAmount = long("base_bet_amount")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}