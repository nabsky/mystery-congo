package com.zorindisplays.host.application.query

data class GetSyncQuery(
    val afterEventId: Long,
    val tableId: Int?
)