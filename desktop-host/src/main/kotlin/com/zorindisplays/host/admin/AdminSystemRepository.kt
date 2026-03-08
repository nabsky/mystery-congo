package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.PendingWinTable
import com.zorindisplays.host.infrastructure.db.tables.SystemStateTable
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
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
        PendingWinTable.deleteAll()

        SystemStateTable.update({ SystemStateTable.id eq 1L }) {
            it[pendingWinId] = null
            it[systemMode] = "ACCEPTING_BETS"
            it[updatedAt] = System.currentTimeMillis()
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