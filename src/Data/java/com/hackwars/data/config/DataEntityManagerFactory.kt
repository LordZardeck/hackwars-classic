package com.hackwars.data.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence

object DataEntityManagerFactory {
    fun create(properties: DataJpaProperties): ManagedEntityManagerFactory {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                poolName = "HackwarsDataPool"
                driverClassName = "com.mysql.cj.jdbc.Driver"
                jdbcUrl = properties.jdbcUrl
                username = properties.username
                password = properties.password
                maximumPoolSize = properties.maximumPoolSize
                minimumIdle = properties.minimumIdle
                connectionTimeout = properties.connectionTimeoutMs
                idleTimeout = properties.idleTimeoutMs
                maxLifetime = properties.maxLifetimeMs
                initializationFailTimeout = -1
            }
        )
        val overrides = mapOf(
            "jakarta.persistence.nonJtaDataSource" to dataSource,
            "hibernate.hbm2ddl.auto" to "none",
            "hibernate.show_sql" to properties.showSql.toString(),
            "hibernate.format_sql" to "true",
            "hibernate.dialect" to "org.hibernate.dialect.MySQLDialect",
            "hibernate.boot.allow_jdbc_metadata_access" to "false",
            "hibernate.temp.use_jdbc_metadata_defaults" to "false",
        )
        val entityManagerFactory = Persistence.createEntityManagerFactory("hackwars-data", overrides)
        return ManagedEntityManagerFactory(dataSource, entityManagerFactory)
    }
}

data class ManagedEntityManagerFactory(
    val dataSource: HikariDataSource,
    val entityManagerFactory: EntityManagerFactory,
) : AutoCloseable {
    override fun close() {
        entityManagerFactory.close()
        dataSource.close()
    }
}
