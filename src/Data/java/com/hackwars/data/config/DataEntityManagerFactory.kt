package com.hackwars.data.config

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence

object DataEntityManagerFactory {
    fun create(properties: DataJpaProperties): EntityManagerFactory {
        val overrides = mapOf(
            "jakarta.persistence.jdbc.driver" to "com.mysql.cj.jdbc.Driver",
            "jakarta.persistence.jdbc.url" to properties.jdbcUrl,
            "jakarta.persistence.jdbc.user" to properties.username,
            "jakarta.persistence.jdbc.password" to properties.password,
            "hibernate.hbm2ddl.auto" to "none",
            "hibernate.show_sql" to properties.showSql.toString(),
            "hibernate.format_sql" to "true",
            "hibernate.dialect" to "org.hibernate.dialect.MySQLDialect",
        )
        return Persistence.createEntityManagerFactory("hackwars-data", overrides)
    }
}
