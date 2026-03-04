package com.zorindisplays.display.host.db

import androidx.room.*

@Dao
interface HostDao {
    @Insert
    suspend fun appendEvent(event: EventRow): Long

    @Query("SELECT * FROM event_log WHERE eventId > :eventId ORDER BY eventId ASC LIMIT :limit")
    suspend fun getEventsAfter(eventId: Long, limit: Int = 500): List<EventRow>

    @Query("SELECT * FROM jackpot_state")
    suspend fun getJackpotState(): List<JackpotStateRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setJackpotState(vararg jackpots: JackpotStateRow)

    @Query("SELECT * FROM global_state WHERE id = 1")
    suspend fun getGlobalState(): GlobalStateRow?

    @Query("UPDATE global_state SET stateVersion = stateVersion + 1, lastEventId = :newLastEventId, updatedAt = :updatedAt WHERE id = 1")
    suspend fun bumpStateVersionAndLastEventId(newLastEventId: Long, updatedAt: Long)
}

