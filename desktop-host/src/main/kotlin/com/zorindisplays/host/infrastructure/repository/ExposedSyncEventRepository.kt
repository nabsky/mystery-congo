package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.event.SyncEvent
import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.SyncEventTable
import org.jetbrains.exposed.sql.selectAll

class ExposedSyncEventRepository {
    suspend fun getAfter(eventId: Long): List<SyncEvent> = dbQuery {
        SyncEventTable.selectAll()
            .where { SyncEventTable.eventId greater eventId }
            .orderBy(SyncEventTable.eventId)
            .map { row ->
                SyncEvent(
                    eventId = row[SyncEventTable.eventId],
                    ts = row[SyncEventTable.createdAt],
                    type = SyncEventType.valueOf(row[SyncEventTable.type]),
                    payloadJson = row[SyncEventTable.payloadJson],
                    stateVersion = row[SyncEventTable.stateVersion]
                )
            }
    }
    suspend fun getLastEventId(): Long = dbQuery {
        SyncEventTable.selectAll()
            .orderBy(SyncEventTable.eventId, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(SyncEventTable.eventId)
            ?: 0L
    }
}