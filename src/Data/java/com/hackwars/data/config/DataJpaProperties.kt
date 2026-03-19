package com.hackwars.data.config

data class DataJpaProperties(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val showSql: Boolean = false,
) {
    companion object {
        fun localhostDefaults(): DataJpaProperties =
            DataJpaProperties(
                jdbcUrl = "jdbc:mysql://127.0.0.1:3306/hackwars",
                username = "root",
                password = "",
                showSql = false,
            )
    }
}
