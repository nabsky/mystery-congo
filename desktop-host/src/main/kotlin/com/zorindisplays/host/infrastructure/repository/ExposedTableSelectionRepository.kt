package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.TableActiveBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableRecentBoxTable
import com.zorindisplays.host.infrastructure.db.tables.TableStateTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedTableSelectionRepository {
    suspend fun toggleBox(tableId: Int, boxId: Int) = dbQuery {
        val exists = TableActiveBoxTable.selectAll()
            .where {
                (TableActiveBoxTable.tableId eq tableId) and (TableActiveBoxTable.boxId eq boxId)
            }.any()
        if (exists) {
            TableActiveBoxTable.deleteWhere {
                (TableActiveBoxTable.tableId eq tableId) and (TableActiveBoxTable.boxId eq boxId)
            }
        } else {
            TableActiveBoxTable.insert {
                it[TableActiveBoxTable.tableId] = tableId
                it[TableActiveBoxTable.boxId] = boxId
                it[selectedAt] = Instant.now().toEpochMilli()
            }
        }
    }

    suspend fun getActiveBoxes(tableId: Int): Set<Int> = dbQuery {
        TableActiveBoxTable.selectAll()
            .where { TableActiveBoxTable.tableId eq tableId }
            .map { it[TableActiveBoxTable.boxId] }.toSet()
    }

    suspend fun clearActiveBoxes(tableId: Int) = dbQuery {
        TableActiveBoxTable.deleteWhere { TableActiveBoxTable.tableId eq tableId }
    }

    suspend fun markRecentBoxes(tableId: Int, boxIds: Set<Int>, ttlMs: Long) = dbQuery {
        val now = Instant.now().toEpochMilli()
        for (boxId in boxIds) {
            TableRecentBoxTable.deleteWhere {
                (TableRecentBoxTable.tableId eq tableId) and (TableRecentBoxTable.boxId eq boxId)
            }
            TableRecentBoxTable.insert {
                it[TableRecentBoxTable.tableId] = tableId
                it[TableRecentBoxTable.boxId] = boxId
                it[confirmedAt] = now
                it[expiresAt] = now + ttlMs
            }
        }
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

