package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.JackpotConfigTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class AdminSettingsRepository {

    suspend fun getJackpotSettings(): List<AdminJackpotSettingsDto> = dbQuery {
        JackpotConfigTable.selectAll()
            .map { row ->
                AdminJackpotSettingsDto(
                    jackpotId = row[JackpotConfigTable.jackpotId],
                    resetAmount = row[JackpotConfigTable.resetAmount],
                    contributionPerBet = row[JackpotConfigTable.contributionPerBet],
                    hitFrequencyGames = row[JackpotConfigTable.hitFrequencyGames],
                    priorityOrder = row[JackpotConfigTable.priorityOrder],
                    enabled = row[JackpotConfigTable.enabled]
                )
            }
            .sortedBy { it.priorityOrder }
    }

    suspend fun updateJackpotSettings(
        jackpotId: String,
        request: UpdateAdminJackpotSettingsRequest
    ) = dbQuery {
        JackpotConfigTable.update({ JackpotConfigTable.jackpotId eq jackpotId }) {
            it[resetAmount] = request.resetAmount
            it[contributionPerBet] = request.contributionPerBet
            it[hitFrequencyGames] = request.hitFrequencyGames
            it[priorityOrder] = request.priorityOrder
            it[enabled] = request.enabled
            it[updatedAt] = System.currentTimeMillis()
        }
    }
}