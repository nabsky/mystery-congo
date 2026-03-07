package com.zorindisplays.host.domain.model

data class TableState(
    val tableId: Int,
    val activeBoxes: Set<Int>,
    val recentBoxes: Set<Int>,
    val isActive: Boolean,
    val lastSeenAt: Long?
)

