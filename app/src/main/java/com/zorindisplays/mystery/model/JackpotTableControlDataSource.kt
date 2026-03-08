package com.zorindisplays.mystery.model

interface JackpotTableControlDataSource : JackpotStateDataSource {

    suspend fun toggleBox(tableId: Int, boxId: Int)

    suspend fun confirmBets(tableId: Int)

    suspend fun selectPayoutBox(tableId: Int, boxId: Int)

    suspend fun confirmPayout(tableId: Int)
}