package com.zorindisplays.mystery.host.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [EventLogRow::class, JackpotStateRow::class, GlobalStateRow::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomJsonConverters::class)
abstract class HostDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao

    companion object {
        @Volatile
        private var INSTANCE: HostDatabase? = null

        fun getInstance(context: Context): HostDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HostDatabase::class.java,
                    "host_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
