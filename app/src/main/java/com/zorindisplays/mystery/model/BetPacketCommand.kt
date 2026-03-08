package com.zorindisplays.mystery.model

data class BetPacketCommand(
    val tableId: Int,
    val toggledBoxId: Int? = null
    // ...другие поля при необходимости
)

