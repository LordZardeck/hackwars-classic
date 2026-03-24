package com.hackwars.rewrite.persistence

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import java.sql.Connection

object RewriteLiquibase {
    const val CHANGELOG_PATH = "db/changelog/db.changelog-master.xml"

    fun update(connection: Connection) {
        run(connection) { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
        }
    }

    fun rollback(connection: Connection, changeSetCount: Int = 1) {
        run(connection) { liquibase ->
            liquibase.rollback(changeSetCount, Contexts(), LabelExpression())
        }
    }

    private fun run(connection: Connection, action: (Liquibase) -> Unit) {
        connection.autoCommit = true
        val database: Database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(JdbcConnection(connection))

        val resourceAccessor = ClassLoaderResourceAccessor(Thread.currentThread().contextClassLoader)
        val liquibase = Liquibase(CHANGELOG_PATH, resourceAccessor, database)
        action(liquibase)
    }
}
