package com.zorindisplays.host.application.service

import com.zorindisplays.host.api.dto.DemoStateDto
import com.zorindisplays.host.api.dto.HealthResponse
import com.zorindisplays.host.api.dto.SyncEventDto
import com.zorindisplays.host.api.dto.SyncResponse
import com.zorindisplays.host.application.query.GetSnapshotQuery
import com.zorindisplays.host.application.query.GetSyncQuery
import com.zorindisplays.host.infrastructure.projection.SnapshotProjection
import com.zorindisplays.host.infrastructure.repository.ExposedStateRepository
import com.zorindisplays.host.infrastructure.repository.ExposedSyncEventRepository
import com.zorindisplays.host.infrastructure.repository.ExposedTableSelectionRepository
import java.time.Instant

class DefaultQueryService(
    private val stateRepository: ExposedStateRepository,
    private val tableSelectionRepository: ExposedTableSelectionRepository,
    private val syncEventRepository: ExposedSyncEventRepository,
    private val snapshotProjection: SnapshotProjection = SnapshotProjection()
) : QueryService {

    override suspend fun getSnapshot(query: GetSnapshotQuery): DemoStateDto {
        query.tableId?.let {
            tableSelectionRepository.touchTable(it, Instant.now().toEpochMilli())
        }

        val state = stateRepository.getCurrentState() ?: return DemoStateDto.stub()
        return snapshotProjection.project(state)
    }

    override suspend fun getSync(query: GetSyncQuery): SyncResponse {
        query.tableId?.let {
            tableSelectionRepository.touchTable(it, Instant.now().toEpochMilli())
        }

        val state = stateRepository.getCurrentState()
        val events = syncEventRepository.getAfter(query.afterEventId)

        return SyncResponse(
            stateVersion = state?.stateVersion ?: 0L,
            lastEventId = state?.lastEventId ?: 0L,
            events = events.map {
                SyncEventDto(
                    eventId = it.eventId,
                    ts = it.ts,
                    type = it.type.name,
                    payloadJson = it.payloadJson
                )
            }
        )
    }

    override suspend fun getHealth(): HealthResponse {
        val state = stateRepository.getCurrentState()
        return HealthResponse(
            ok = true,
            stateVersion = state?.stateVersion ?: 0L,
            lastEventId = state?.lastEventId ?: 0L
        )
    }
}