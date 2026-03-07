package com.zorindisplays.host.application.service

import com.zorindisplays.host.application.query.*
import com.zorindisplays.host.api.dto.*

interface QueryService {
    suspend fun getSnapshot(query: GetSnapshotQuery): DemoStateDto
    suspend fun getSync(query: GetSyncQuery): SyncResponse
    suspend fun getHealth(): HealthResponse
}

