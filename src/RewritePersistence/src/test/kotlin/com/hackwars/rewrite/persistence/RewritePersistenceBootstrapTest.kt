package com.hackwars.rewrite.persistence

import liquibase.exception.ValidationFailedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.sql.ResultSet

@Testcontainers
class RewritePersistenceBootstrapTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_persistence_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun update_createsExpectedSchemaObjects() {
        withConnection {
            assertDoesNotThrow {
                RewriteLiquibase.update(it)
            }

            assertTableExists(it, "rewrite_import_batch")
            assertTableExists(it, "rewrite_player_account")
            assertTableExists(it, "rewrite_computer_state")
            assertTableExists(it, "rewrite_state_event")
            assertTableExists(it, "rewrite_state_snapshot")
            assertTableExists(it, "rewrite_network_directory")
            assertTableExists(it, "rewrite_network_link")
            assertTableExists(it, "rewrite_network_npc")
        }
    }

    @Test
    fun rollbackRemovesBootstrapTables() {
        withConnection {
            RewriteLiquibase.update(it)
            RewriteLiquibase.rollback(it, 2)

            assertTableMissing(it, "rewrite_import_batch")
            assertTableMissing(it, "rewrite_player_account")
            assertTableMissing(it, "rewrite_computer_state")
            assertTableMissing(it, "rewrite_state_event")
            assertTableMissing(it, "rewrite_state_snapshot")
            assertTableMissing(it, "rewrite_network_directory")
            assertTableMissing(it, "rewrite_network_link")
            assertTableMissing(it, "rewrite_network_npc")
        }
    }

    @Test
    fun importerPlannerBuildsSeedBatchWithoutTouchingDatabase() {
        val coordinator = LegacyImportCoordinator()

        val mysqlBatch = coordinator.plan(
            LegacyMySqlDumpDescriptor(
                sourceLocation = "/legacy/hackwars.sql",
                databaseName = "hackwars",
            )
        )
        val xmlBatch = coordinator.plan(
            LegacyXmlDescriptor(
                sourceLocation = "/legacy/hackwars.xml",
                rootElement = "computer",
            )
        )
        val jsonBatch = coordinator.plan(
            LegacyJsonDescriptor(
                sourceLocation = "/legacy/backup.json",
                documentType = "inventory",
            )
        )

        assertEquals("mysql-dump:/legacy/hackwars.sql", mysqlBatch.batchId)
        assertTrue(mysqlBatch.seedPayload is SeedPlayerAccount)
        assertEquals("PF-HACKWARS", (mysqlBatch.seedPayload as SeedPlayerAccount).playFabId)

        assertEquals("xml:/legacy/hackwars.xml", xmlBatch.batchId)
        assertTrue(xmlBatch.seedPayload is SeedComputerState)
        assertEquals("computer", (xmlBatch.seedPayload as SeedComputerState).computerId)

        assertEquals("json:/legacy/backup.json", jsonBatch.batchId)
        assertTrue(jsonBatch.seedPayload is SeedInventorySnapshot)
        assertEquals(listOf("inventory"), (jsonBatch.seedPayload as SeedInventorySnapshot).notes)
    }

    @Test
    fun seedSinkRecordsBatchesForLaterDatabaseWriting() {
        val sink = SeedBatchRecorder()
        val batch = LegacyImportCoordinator().plan(
            LegacyJsonDescriptor(
                sourceLocation = "/legacy/seed.json",
                documentType = "economy",
            )
        )

        assertDoesNotThrow {
            kotlinx.coroutines.test.runTest {
                sink.write(batch)
            }
        }

        assertEquals(listOf(batch), sink.snapshot())
    }

    private fun withConnection(block: (java.sql.Connection) -> Unit) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)
    }

    private fun assertTableExists(connection: java.sql.Connection, tableName: String) {
        assertTrue(tableExists(connection, tableName), "Expected table $tableName to exist")
    }

    private fun assertTableMissing(connection: java.sql.Connection, tableName: String) {
        assertFalse(tableExists(connection, tableName), "Expected table $tableName to be removed")
    }

    private fun tableExists(connection: java.sql.Connection, tableName: String): Boolean {
        connection.prepareStatement(
            """
            select count(*)
            from information_schema.tables
            where table_schema = current_schema()
              and table_name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getInt(1) > 0
            }
        }
    }
}
