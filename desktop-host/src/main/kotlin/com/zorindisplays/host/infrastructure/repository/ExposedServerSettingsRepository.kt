package com.zorindisplays.host.infrastructure.repository

import com.zorindisplays.host.infrastructure.db.dbQuery
import com.zorindisplays.host.infrastructure.db.tables.ServerSettingsTable
import org.jetbrains.exposed.sql.selectAll

class ExposedServerSettingsRepository {
    suspend fun getCurrencyCode(): String = dbQuery {
        ServerSettingsTable.selectAll().single()[ServerSettingsTable.currencyCode]
    }

    suspend fun getBaseBetAmount(): Long = dbQuery {
        ServerSettingsTable.selectAll().single()[ServerSettingsTable.baseBetAmount]
    }
}