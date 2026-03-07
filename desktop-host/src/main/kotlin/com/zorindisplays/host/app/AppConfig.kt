package com.zorindisplays.host.app

data class AppConfig(
    val host: String,
    val port: Int,
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String,
    val activeTableTimeoutMs: Long,
    val recentBoxTtlMs: Long,
    val dbPoolMaxSize: Int,
    val dbPoolMinIdle: Int,
    val tableCount: Int,
    val boxCount: Int,
    val adminUsername: String,
    val adminPassword: String
) {
    companion object {
        fun fromEnv(): AppConfig {
            return AppConfig(
                host = System.getenv("HOST") ?: "0.0.0.0",
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/mystery",
                jdbcUser = System.getenv("JDBC_USER") ?: "postgres",
                jdbcPassword = System.getenv("JDBC_PASSWORD") ?: "postgres",
                activeTableTimeoutMs = System.getenv("ACTIVE_TABLE_TIMEOUT_MS")?.toLongOrNull() ?: 60000L,
                recentBoxTtlMs = System.getenv("RECENT_BOX_TTL_MS")?.toLongOrNull() ?: 30000L,
                dbPoolMaxSize = System.getenv("DB_POOL_MAX_SIZE")?.toIntOrNull() ?: 10,
                dbPoolMinIdle = System.getenv("DB_POOL_MIN_IDLE")?.toIntOrNull() ?: 2,
                tableCount = System.getenv("TABLE_COUNT")?.toIntOrNull() ?: 8,
                boxCount = System.getenv("BOX_COUNT")?.toIntOrNull() ?: 9,
                adminUsername = System.getenv("ADMIN_USERNAME") ?: "admin",
                adminPassword = System.getenv("ADMIN_PASSWORD") ?: "admin123",
            )
        }
    }
}
