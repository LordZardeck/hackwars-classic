package com.hackwars.data.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.slf4j.LoggerFactory

object DataEntityManagerFactory {
    fun create(properties: DataJpaProperties): ManagedEntityManagerFactory {
        if (properties.suppressVerboseHibernateResultLogs) {
            suppressVerboseHibernateResultLogs()
        }
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

    private fun suppressVerboseHibernateResultLogs() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return

        // These categories emit bound parameter values and extracted result data.
        val noisyLoggerNames = listOf(
            "org.hibernate.orm.jdbc.bind",
            "org.hibernate.orm.jdbc.extract",
            "org.hibernate.orm.results",
            "org.hibernate.type.descriptor.sql.BasicBinder",
            "org.hibernate.type.descriptor.sql.BasicExtractor",
        )

        noisyLoggerNames.forEach { loggerName ->
            loggerContext.getLogger(loggerName).level = Level.WARN
        }
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
