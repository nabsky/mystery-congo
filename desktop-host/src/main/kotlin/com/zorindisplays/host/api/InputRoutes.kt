package com.zorindisplays.host.api

import com.zorindisplays.host.api.dto.ConfirmPayoutRequest
import com.zorindisplays.host.api.dto.ConfirmRequest
import com.zorindisplays.host.api.dto.SelectPayoutBoxRequest
import com.zorindisplays.host.api.dto.ToggleRequest
import com.zorindisplays.host.application.command.ConfirmBetsCommand
import com.zorindisplays.host.application.command.ConfirmPayoutCommand
import com.zorindisplays.host.application.command.SelectPayoutBoxCommand
import com.zorindisplays.host.application.command.ToggleBoxCommand
import com.zorindisplays.host.application.service.CommandService
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.registerInputRoutes(commandService: CommandService) {
    post("/input/toggle") {
        val req = call.receive<ToggleRequest>()
        val result = commandService.toggleBox(
            ToggleBoxCommand(
                tableId = req.tableId,
                boxId = req.boxId
            )
        )
        call.respondCommandResult(result)
    }

    post("/input/confirm") {
        val req = call.receive<ConfirmRequest>()
        val result = commandService.confirmBets(
            ConfirmBetsCommand(
                tableId = req.tableId
            )
        )
        call.respondCommandResult(result)
    }

    post("/input/payout/selectBox") {
        val req = call.receive<SelectPayoutBoxRequest>()
        val result = commandService.selectPayoutBox(
            SelectPayoutBoxCommand(
                tableId = req.tableId,
                boxId = req.boxId
            )
        )
        call.respondCommandResult(result)
    }

    post("/input/payout/confirm") {
        val req = call.receive<ConfirmPayoutRequest>()
        val result = commandService.confirmPayout(
            ConfirmPayoutCommand(
                tableId = req.tableId
            )
        )
        call.respondCommandResult(result)
    }
}