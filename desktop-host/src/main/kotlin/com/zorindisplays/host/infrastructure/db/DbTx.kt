package com.zorindisplays.host.infrastructure.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Transaction
import kotlinx.coroutines.withContext

suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.IO) {
        transaction { block() }
    }

