package com.zorindisplays.host.domain.model

data class HostState(
    val stateVersion: Long,
    val lastEventId: Long,
    val systemMode: SystemMode,
    val pendingWin: PendingWin?,
    val jackpots: Map<JackpotId, JackpotState>,
    val tables: List<TableState>,
    val currencyCode: String,
)