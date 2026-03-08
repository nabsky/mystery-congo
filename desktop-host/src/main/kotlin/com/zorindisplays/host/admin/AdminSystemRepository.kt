package com.zorindisplays.host.admin

import com.zorindisplays.host.domain.event.SyncEventType
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SyncEventTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class AdminSystemRepository {

    suspend fun clearActiveBoxes(): AdminSystemActionResponse = dbQuery {
        val deleted = TableActiveBoxTable.deleteAll()
        AdminSystemActionResponse(
            ok = true,
            message = "Cleared active boxes ($deleted rows)"
        )
    }

    suspend fun clearRecentBoxes(): AdminSystemActionResponse = dbQuery {
        val deleted = TableRecentBoxTable.deleteAll()
        AdminSystemActionResponse(
            ok = true,
            message = "Cleared recent boxes ($deleted rows)"
        )
    }

    suspend fun resetPendingWin(): AdminSystemActionResponse = dbQuery {
        val now = System.currentTimeMillis()

        val systemRow = SystemStateTable.selectAll().single()
        val currentStateVersion = systemRow[SystemStateTable.stateVersion]
        val currentPendingWinId = systemRow[SystemStateTable.pendingWinId]

        PendingWinTable.deleteAll()

        val nextStateVersion = currentStateVersion + 1

        val eventId = SyncEventTable.insert {
            it[createdAt] = now
            it[type] = SyncEventType.PendingWinReset.name
            it[payloadJson] =
                """{"pendingWinId":${currentPendingWinId ?: "null"},"source":"admin"}"""
            it[stateVersion] = nextStateVersion
        } get SyncEventTable.eventId

        SystemStateTable.update({ SystemStateTable.id eq 1L }) {
            it[pendingWinId] = null
            it[systemMode] = "ACCEPTING_BETS"
            it[stateVersion] = nextStateVersion
            it[lastEventId] = eventId
            it[updatedAt] = now
        }

        AdminSystemActionResponse(
            ok = true,
            message = "Pending win reset"
        )
    }

    suspend fun refresh(): AdminSystemActionResponse {
        return AdminSystemActionResponse(
            ok = true,
            message = "Refresh requested"
        )
    }
}