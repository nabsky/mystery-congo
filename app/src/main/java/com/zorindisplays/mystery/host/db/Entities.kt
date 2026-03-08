package com.zorindisplays.mystery.host.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "event_log")
data class EventLogRow(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val ts: Long,
    val type: String,
    val payloadJson: String
)

@Entity(tableName = "jackpot_state")
data class JackpotStateRow(
    @PrimaryKey val jackpotId: String,
    val currentAmount: Long,
    val updatedAt: Long
)

@Entity(tableName = "global_state")
data class GlobalStateRow(
    @PrimaryKey val id: Int = 1,
    val stateVersion: Long,
    val lastEventId: Long,
    val updatedAt: Long
)
