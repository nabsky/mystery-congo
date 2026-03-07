package com.zorindisplays.host.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminBetBatchDto(
    val id: Long,
    val tableId: Int,
    val confirmedAt: Long,
    val boxCount: Int,
    val result: String?,
    val winningJackpotId: String?,
    val winningBoxId: Int?,
    val items: List<AdminBetBatchItemDto>
)

@Serializable
data class AdminBetBatchItemDto(
    val id: Long,
    val betBatchId: Long,
    val boxId: Int,
    val seqNo: Int,
    val result: String?
)

@Serializable
data class AdminJackpotHitDto(
    val id: Long,
    val jackpotId: String,
    val tableId: Int,
    val triggerBoxId: Int,
    val winAmount: Long,
    val betBatchId: Long?,
    val hitAt: Long,
    val payoutConfirmedAt: Long?,
    val confirmedBoxId: Int?,
    val status: String
)

@Serializable
data class AdminPendingWinDto(
    val id: Long,
    val jackpotId: String,
    val tableId: Int,
    val winningBoxId: Int,
    val dealerConfirmed: Boolean,
    val winAmount: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)