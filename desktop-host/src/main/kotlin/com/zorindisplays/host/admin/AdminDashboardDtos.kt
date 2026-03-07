package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminDashboardDto(
    val systemMode: String,
    val pendingWin: AdminDashboardPendingWinDto?,
    val jackpots: Map<String, Long>,
    val activeTablesCount: Int,
    val totalTables: Int,
    val totalBatchesToday: Long,
    val totalHitsToday: Long,
    val latestHit: AdminDashboardLatestHitDto?,
    val latestBatches: List<AdminDashboardLatestBatchDto>
)

@Serializable
data class AdminDashboardPendingWinDto(
    val jackpotId: String,
    val tableId: Int,
    val winningBoxId: Int,
    val dealerConfirmed: Boolean,
    val winAmount: Long
)

@Serializable
data class AdminDashboardLatestHitDto(
    val jackpotId: String,
    val tableId: Int,
    val triggerBoxId: Int,
    val winAmount: Long,
    val hitAt: Long,
    val status: String
)

@Serializable
data class AdminDashboardLatestBatchDto(
    val id: Long,
    val tableId: Int,
    val confirmedAt: Long,
    val result: String?,
    val winningJackpotId: String?,
    val winningBoxId: Int?
)