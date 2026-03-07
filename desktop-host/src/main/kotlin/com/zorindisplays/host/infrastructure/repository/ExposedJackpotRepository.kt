package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.domain.model.JackpotConfig
import com.zorindisplays.host.domain.model.JackpotId
import com.zorindisplays.host.domain.model.JackpotState
import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.JackpotConfigTable
import com.zorindisplays.host.infrastructure.db.tables.JackpotStateTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedJackpotRepository {

    suspend fun getConfigs(): List<JackpotConfig> = dbQuery {
        JackpotConfigTable.selectAll()
            .map { row ->
                JackpotConfig(
                    jackpotId = JackpotId.valueOf(row[JackpotConfigTable.jackpotId]),
                    resetAmount = row[JackpotConfigTable.resetAmount],
                    contributionPerBet = row[JackpotConfigTable.contributionPerBet],
                    hitFrequencyGames = row[JackpotConfigTable.hitFrequencyGames],
                    priorityOrder = row[JackpotConfigTable.priorityOrder],
                    enabled = row[JackpotConfigTable.enabled]
                )
            }
            .sortedBy { it.priorityOrder }
    }

    suspend fun getStates(): List<JackpotState> = dbQuery {
        JackpotStateTable.selectAll()
            .map { row ->
                JackpotState(
                    jackpotId = JackpotId.valueOf(row[JackpotStateTable.jackpotId]),
                    currentAmount = row[JackpotStateTable.currentAmount],
                    gamesSinceLastHit = row[JackpotStateTable.gamesSinceLastHit]
                )
            }
    }

    suspend fun getConfig(jackpotId: JackpotId): JackpotConfig? = dbQuery {
        JackpotConfigTable.selectAll()
            .where { JackpotConfigTable.jackpotId eq jackpotId.name }
            .singleOrNull()
            ?.let { row ->
                JackpotConfig(
                    jackpotId = jackpotId,
                    resetAmount = row[JackpotConfigTable.resetAmount],
                    contributionPerBet = row[JackpotConfigTable.contributionPerBet],
                    hitFrequencyGames = row[JackpotConfigTable.hitFrequencyGames],
                    priorityOrder = row[JackpotConfigTable.priorityOrder],
                    enabled = row[JackpotConfigTable.enabled]
                )
            }
    }
}