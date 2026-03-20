package com.hackwars.data.config

data class DataJpaProperties(
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val database: String = "hackwars",
    val username: String = "root",
    val password: String = "",
    val showSql: Boolean = false,
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 1,
    val connectionTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 600_000,
    val maxLifetimeMs: Long = 1_800_000,
) {
    val jdbcUrl: String
        get() = "jdbc:mysql://$host:$port/$database"

    companion object {
        fun localhostDefaults(): DataJpaProperties =
            DataJpaProperties()
    }
}
