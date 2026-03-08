package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.DevicePresenceTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere

class AdminDevicesRepository {

    companion object {
        private const val ONLINE_TTL_MS = 15_000L
        private const val STALE_DELETE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    suspend fun heartbeat(request: DeviceHeartbeatRequest): DeviceHeartbeatResponse = dbQuery {
        val now = System.currentTimeMillis()

        DevicePresenceTable.deleteWhere {
            DevicePresenceTable.lastSeenAt less (now - STALE_DELETE_TTL_MS)
        }

        val exists = DevicePresenceTable.selectAll()
            .where { DevicePresenceTable.deviceId eq request.deviceId }
            .any()

        if (exists) {
            DevicePresenceTable.update({ DevicePresenceTable.deviceId eq request.deviceId }) {
                it[deviceType] = request.deviceType
                it[deviceName] = request.deviceName
                it[tableId] = request.tableId
                it[appVersion] = request.appVersion
                it[lastStateVersion] = request.lastStateVersion
                it[lastEventId] = request.lastEventId
                it[lastSeenAt] = now
                it[updatedAt] = now
            }
        } else {
            DevicePresenceTable.insert {
                it[deviceId] = request.deviceId
                it[deviceType] = request.deviceType
                it[deviceName] = request.deviceName
                it[tableId] = request.tableId
                it[appVersion] = request.appVersion
                it[lastStateVersion] = request.lastStateVersion
                it[lastEventId] = request.lastEventId
                it[lastSeenAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        DeviceHeartbeatResponse(ok = true)
    }

    suspend fun getOnlineSummary(): AdminDevicePresenceSummaryDto = dbQuery {
        val now = System.currentTimeMillis()

        val devices = DevicePresenceTable.selectAll()
            .map { row ->
                val lastSeenAt = row[DevicePresenceTable.lastSeenAt]
                val isOnline = now - lastSeenAt <= ONLINE_TTL_MS

                AdminDevicePresenceDto(
                    deviceId = row[DevicePresenceTable.deviceId],
                    deviceType = row[DevicePresenceTable.deviceType],
                    deviceName = row[DevicePresenceTable.deviceName],
                    tableId = row[DevicePresenceTable.tableId],
                    appVersion = row[DevicePresenceTable.appVersion],
                    lastStateVersion = row[DevicePresenceTable.lastStateVersion],
                    lastEventId = row[DevicePresenceTable.lastEventId],
                    lastSeenAt = lastSeenAt,
                    isOnline = isOnline
                )
            }
            .filter { it.isOnline }
            .sortedWith(
                compareBy<AdminDevicePresenceDto> { it.deviceType }
                    .thenBy { it.tableId ?: 0 }
                    .thenBy { it.deviceName ?: "" }
                    .thenBy { it.deviceId }
            )

        AdminDevicePresenceSummaryDto(
            displaysOnline = devices.count { it.deviceType == "DISPLAY" },
            tablesOnline = devices.count { it.deviceType == "TABLE" },
            devices = devices
        )
    }
}