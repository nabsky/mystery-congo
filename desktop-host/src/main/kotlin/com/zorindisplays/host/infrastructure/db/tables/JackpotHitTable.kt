package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object JackpotHitTable : Table("jackpot_hit") {
    val id = long("id").autoIncrement()
    val jackpotId = varchar("jackpot_id", 16)
    val tableId = integer("table_id")
    val triggerBoxId = integer("trigger_box_id")
    val winAmount = long("win_amount")
    val betBatchId = long("bet_batch_id").nullable()
    val hitAt = long("hit_at")
    val payoutConfirmedAt = long("payout_confirmed_at").nullable()
    val confirmedBoxId = integer("confirmed_box_id").nullable()
    val status = varchar("status", 32)
    init {
        index(false, jackpotId)
        index(false, hitAt)
    }

    override val primaryKey = PrimaryKey(id)
}

