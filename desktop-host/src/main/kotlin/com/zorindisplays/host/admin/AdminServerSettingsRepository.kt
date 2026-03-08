package com.zorindisplays.host.admin

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.ServerSettingsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class AdminServerSettingsRepository {

    suspend fun getSettings(): AdminServerSettingsDto = dbQuery {
        val row = ServerSettingsTable.selectAll()
            .where { ServerSettingsTable.id eq 1L }
            .single()

        AdminServerSettingsDto(
            currencyCode = row[ServerSettingsTable.currencyCode],
            baseBetAmount = row[ServerSettingsTable.baseBetAmount]
        )
    }

    suspend fun updateSettings(request: UpdateServerSettingsRequest): AdminServerSettingsDto = dbQuery {
        val now = System.currentTimeMillis()
        val normalizedCurrency = request.currencyCode.trim().uppercase()

        require(normalizedCurrency.length == 3) { "currencyCode must be 3 letters" }
        require(request.baseBetAmount >= 0) { "baseBetAmount must be >= 0" }

        ServerSettingsTable.update({ ServerSettingsTable.id eq 1L }) {
            it[currencyCode] = normalizedCurrency
            it[baseBetAmount] = request.baseBetAmount
            it[updatedAt] = now
        }

        AdminServerSettingsDto(
            currencyCode = normalizedCurrency,
            baseBetAmount = request.baseBetAmount
        )
    }
}