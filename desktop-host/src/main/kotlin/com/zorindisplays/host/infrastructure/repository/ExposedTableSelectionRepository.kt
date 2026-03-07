package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedTableSelectionRepository {
    suspend fun getActiveBoxes(tableId: Int): Set<Int> = dbQuery {
        TableActiveBoxTable.selectAll()
            .where { TableActiveBoxTable.tableId eq tableId }
            .map { it[TableActiveBoxTable.boxId] }.toSet()
    }
    suspend fun getRecentBoxes(tableId: Int, nowMs: Long): Set<Int> = dbQuery {
        TableRecentBoxTable.selectAll()
            .where {
                (TableRecentBoxTable.tableId eq tableId) and (TableRecentBoxTable.expiresAt greater nowMs)
            }.map { it[TableRecentBoxTable.boxId] }.toSet()
    }
    suspend fun touchTable(tableId: Int, timestampMs: Long) = dbQuery {
        TableStateTable.update({ TableStateTable.tableId eq tableId }) {
            it[lastSeenAt] = timestampMs
            it[updatedAt] = timestampMs
        }
    }
    suspend fun getLastSeenAt(tableId: Int): Long? = dbQuery {
        TableStateTable.selectAll()
            .where { TableStateTable.tableId eq tableId }
            .singleOrNull()?.get(TableStateTable.lastSeenAt)
    }
}

