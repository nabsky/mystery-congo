package com.zorindisplays.display.model

data class BetPacketCommand(
    val tableId: Int,
    val toggledBoxId: Int? = null
    // ...другие поля при необходимости
)

