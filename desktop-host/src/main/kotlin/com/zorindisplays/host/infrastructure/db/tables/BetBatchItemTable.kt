package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object BetBatchItemTable : Table("bet_batch_item") {
    val id = long("id").autoIncrement()
    val betBatchId = long("bet_batch_id")
    val boxId = integer("box_id")
    val seqNo = integer("seq_no")
    val result = varchar("result", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}