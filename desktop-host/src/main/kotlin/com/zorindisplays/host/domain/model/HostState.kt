package com.zorindisplays.host.domain.model

data class HostState(
    val stateVersion: Long,
    val lastEventId: Long,
    val systemMode: SystemMode,
    val pendingWin: PendingWin?,
    val jackpots: Map<JackpotId, JackpotState>,
    val jackpotGrowth: Map<JackpotId, Long> = emptyMap(),
    val jackpotSettings: Map<JackpotId, JackpotSnapshotSettings> = emptyMap(),
    val tables: List<TableState>,
    val currencyCode: String,
)

data class JackpotSnapshotSettings(
    val resetAmount: Long,
    val contributionPerBet: Long,
    val hitFrequencyGames: Int,
)