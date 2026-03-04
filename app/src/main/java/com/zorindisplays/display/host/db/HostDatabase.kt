package com.zorindisplays.display.host.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EventRow::class, JackpotStateRow::class, GlobalStateRow::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomJsonConverters::class)
abstract class HostDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
}

