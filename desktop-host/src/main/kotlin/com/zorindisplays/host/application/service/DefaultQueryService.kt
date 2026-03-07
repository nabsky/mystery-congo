package com.zorindisplays.host.application.service

import com.zorindisplays.host.api.dto.HealthResponse
import com.zorindisplays.host.api.dto.SnapshotResponse
import com.zorindisplays.host.api.dto.SyncEventDto
import com.zorindisplays.host.api.dto.SyncResponse
import com.zorindisplays.host.application.query.GetSnapshotQuery
import com.zorindisplays.host.application.query.GetSyncQuery
import com.zorindisplays.host.infrastructure.projection.SnapshotProjection
import com.zorindisplays.host.infrastructure.repository.ExposedStateRepository
import com.zorindisplays.host.infrastructure.repository.ExposedSyncEventRepository
import com.zorindisplays.host.infrastructure.repository.ExposedTableSelectionRepository

class DefaultQueryService(
    private val stateRepository: ExposedStateRepository,
    private val tableSelectionRepository: ExposedTableSelectionRepository,
    private val syncEventRepository: ExposedSyncEventRepository,
    private val snapshotProjection: SnapshotProjection
) : QueryService {

    override suspend fun getSnapshot(query: GetSnapshotQuery): SnapshotResponse {
        query.tableId?.let { tableId ->
            tableSelectionRepository.touchTable(tableId, System.currentTimeMillis())
        }

        val state = stateRepository.getCurrentState()
            ?: error("State not initialized")

        return SnapshotResponse(
            state = snapshotProjection.project(state),
            stateVersion = state.stateVersion,
            lastEventId = state.lastEventId
        )
    }

    override suspend fun getSync(query: GetSyncQuery): SyncResponse {
        query.tableId?.let { tableId ->
            tableSelectionRepository.touchTable(tableId, System.currentTimeMillis())
        }

        val state = stateRepository.getCurrentState()
            ?: error("State not initialized")

        val events = syncEventRepository.getAfter(query.afterEventId)
            .map { event ->
                SyncEventDto(
                    eventId = event.eventId,
                    ts = event.ts,
                    type = event.type.name,
                    payloadJson = event.payloadJson
                )
            }

        return SyncResponse(
            stateVersion = state.stateVersion,
            lastEventId = state.lastEventId,
            events = events
        )
    }

    override suspend fun getHealth(): HealthResponse {
        val state = stateRepository.getCurrentState()
            ?: error("State not initialized")

        return HealthResponse(
            ok = true,
            stateVersion = state.stateVersion,
            lastEventId = state.lastEventId
        )
    }
}