package com.zorindisplays.host.domain.result

sealed class CommandResult {

    object Accepted : CommandResult()

    data class Rejected(
        val reason: String
    ) : CommandResult()

    data class Failed(
        val error: String
    ) : CommandResult()
}