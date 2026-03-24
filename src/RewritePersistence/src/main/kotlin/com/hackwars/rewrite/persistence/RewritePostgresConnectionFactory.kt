package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager

object RewritePostgresConnectionFactory {
    fun fromEnvironment(environment: Map<String, String> = System.getenv()): () -> Connection {
        val jdbcUrl = environment["REWRITE_DB_URL"] ?: defaultJdbcUrl(environment)
        val username = environment["REWRITE_DB_USER"] ?: "hackwars"
        val password = environment["REWRITE_DB_PASSWORD"] ?: "hackwars"
        return {
            DriverManager.getConnection(jdbcUrl, username, password)
        }
    }

    private fun defaultJdbcUrl(environment: Map<String, String>): String {
        val host = environment["REWRITE_DB_HOST"] ?: "127.0.0.1"
        val port = environment["REWRITE_DB_PORT"] ?: "55432"
        val databaseName = environment["REWRITE_DB_NAME"] ?: "hackwars_rewrite"
        return "jdbc:postgresql://$host:$port/$databaseName"
    }
}
