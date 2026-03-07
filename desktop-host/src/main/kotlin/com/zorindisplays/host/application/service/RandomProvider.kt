package com.zorindisplays.host.application.service

import kotlin.random.Random

interface RandomProvider {
    fun nextInt(until: Int): Int
}

object DefaultRandomProvider : RandomProvider {
    override fun nextInt(until: Int): Int = Random.nextInt(until)
}