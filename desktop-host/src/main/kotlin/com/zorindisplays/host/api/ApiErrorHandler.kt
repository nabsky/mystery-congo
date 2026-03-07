package com.zorindisplays.host.api

import io.ktor.server.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.zorindisplays.host.domain.result.CommandResult
import com.zorindisplays.host.api.dto.OkResponse

suspend fun ApplicationCall.respondCommandResult(result: CommandResult) {
    when (result) {
        is CommandResult.Accepted -> respond(OkResponse(ok = true))
        is CommandResult.Rejected -> respond(HttpStatusCode.Conflict, result.reason)
        is CommandResult.Failed -> respond(HttpStatusCode.InternalServerError, result.error)
    }
}
