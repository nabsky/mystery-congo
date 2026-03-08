package com.zorindisplays.host.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

object DevicePresenceTable : Table("device_presence") {
    val deviceId = varchar("device_id", 64)
    val deviceType = varchar("device_type", 16)
    val deviceName = varchar("device_name", 128).nullable()
    val tableId = integer("table_id").nullable()

    val appVersion = varchar("app_version", 32).nullable()
    val lastStateVersion = long("last_state_version")
    val lastEventId = long("last_event_id")

    val lastSeenAt = long("last_seen_at")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(deviceId)
}