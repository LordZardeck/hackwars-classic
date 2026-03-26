package com.hackwars.rewrite.persistence

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class RewritePersistenceMigrationSmokeTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_persistence_migration_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun migrateUpAndRollbackExerciseTheRewriteSchema() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            RewriteLiquibase.update(connection)
            assertTrue(tableExists(connection, "rewrite_state_event"))
            assertTrue(tableExists(connection, "rewrite_state_snapshot"))
            assertTrue(tableExists(connection, "rewrite_network_directory"))
            assertTrue(tableExists(connection, "rewrite_network_link"))
            assertTrue(tableExists(connection, "rewrite_network_npc"))
            assertTrue(tableExists(connection, "rewrite_session_ticket"))
            assertTrue(tableExists(connection, "rewrite_service_session"))
            assertTrue(tableExists(connection, "rewrite_chat_channel"))
            assertTrue(tableExists(connection, "rewrite_chat_channel_member"))
            assertTrue(tableExists(connection, "rewrite_chat_message"))
            assertTrue(tableExists(connection, "rewrite_chat_relation"))
            assertTrue(tableExists(connection, "rewrite_chat_channel_mute"))
            assertTrue(tableExists(connection, "rewrite_chat_presence"))
            assertTrue(tableExists(connection, "rewrite_player_profile"))
            assertTrue(tableExists(connection, "rewrite_computer_projection"))
            assertTrue(tableExists(connection, "rewrite_computer_preference"))
            assertTrue(tableExists(connection, "rewrite_computer_skill_stat"))
            assertTrue(tableExists(connection, "rewrite_website_projection"))

            RewriteLiquibase.rollback(connection, 1)
            assertTrue(tableExists(connection, "rewrite_player_profile"))
            assertTrue(tableExists(connection, "rewrite_computer_projection"))
            assertTrue(tableExists(connection, "rewrite_computer_preference"))
            assertTrue(tableExists(connection, "rewrite_computer_skill_stat"))
            assertEquals(false, tableExists(connection, "rewrite_website_projection"))

            RewriteLiquibase.rollback(connection, 1)
            assertTrue(tableExists(connection, "rewrite_chat_channel"))
            assertTrue(tableExists(connection, "rewrite_chat_channel_member"))
            assertTrue(tableExists(connection, "rewrite_chat_message"))
            assertTrue(tableExists(connection, "rewrite_chat_relation"))
            assertTrue(tableExists(connection, "rewrite_chat_channel_mute"))
            assertTrue(tableExists(connection, "rewrite_chat_presence"))
            assertEquals(false, tableExists(connection, "rewrite_player_profile"))
            assertEquals(false, tableExists(connection, "rewrite_computer_projection"))
            assertEquals(false, tableExists(connection, "rewrite_computer_preference"))
            assertEquals(false, tableExists(connection, "rewrite_computer_skill_stat"))

            RewriteLiquibase.rollback(connection, 1)
            assertTrue(tableExists(connection, "rewrite_state_event"))
            assertTrue(tableExists(connection, "rewrite_state_snapshot"))
            assertTrue(tableExists(connection, "rewrite_network_directory"))
            assertTrue(tableExists(connection, "rewrite_network_link"))
            assertTrue(tableExists(connection, "rewrite_network_npc"))
            assertTrue(tableExists(connection, "rewrite_session_ticket"))
            assertTrue(tableExists(connection, "rewrite_service_session"))
            assertEquals(false, tableExists(connection, "rewrite_chat_channel"))
            assertEquals(false, tableExists(connection, "rewrite_chat_channel_member"))
            assertEquals(false, tableExists(connection, "rewrite_chat_message"))
            assertEquals(false, tableExists(connection, "rewrite_chat_relation"))
            assertEquals(false, tableExists(connection, "rewrite_chat_channel_mute"))
            assertEquals(false, tableExists(connection, "rewrite_chat_presence"))

            RewriteLiquibase.rollback(connection, 1)
            assertTrue(tableExists(connection, "rewrite_state_event"))
            assertTrue(tableExists(connection, "rewrite_state_snapshot"))
            assertTrue(tableExists(connection, "rewrite_network_directory"))
            assertTrue(tableExists(connection, "rewrite_network_link"))
            assertTrue(tableExists(connection, "rewrite_network_npc"))
            assertEquals(false, tableExists(connection, "rewrite_session_ticket"))
            assertEquals(false, tableExists(connection, "rewrite_service_session"))

            RewriteLiquibase.rollback(connection, 1)
            assertTrue(tableExists(connection, "rewrite_state_event"))
            assertTrue(tableExists(connection, "rewrite_state_snapshot"))
            assertEquals(false, tableExists(connection, "rewrite_network_directory"))
            assertEquals(false, tableExists(connection, "rewrite_network_link"))
            assertEquals(false, tableExists(connection, "rewrite_network_npc"))

            RewriteLiquibase.rollback(connection, 1)
            assertEquals(false, tableExists(connection, "rewrite_state_event"))
            assertEquals(false, tableExists(connection, "rewrite_state_snapshot"))
        }
    }

    @Test
    fun migratedSchemaAcceptsAuthPlayerAndComputerImportBatches() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop schema if exists public cascade")
                statement.execute("create schema public")
            }
            RewriteLiquibase.update(connection)
        }

        val sink = JdbcRewriteSeedSink(
            connectionFactory = {
                DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
            },
        )

        runBlocking {
            sink.write(
                RewriteSeedBatch(
                    batchId = "player-import",
                    source = LegacyMySqlDumpDescriptor("/legacy/local.sql", "hackwars"),
                    seedPayload = SeedPlayerAccount(
                        playerId = "local-user",
                        playFabId = "PF-LOCAL",
                        playerIp = "198.51.100.10",
                    ),
                    createdAt = Instant.EPOCH,
                ),
            )
            sink.write(
                RewriteSeedBatch(
                    batchId = "ticket-import",
                    source = LegacyMySqlDumpDescriptor("/legacy/forum.sql", "hackwars"),
                    seedPayload = PersistedSessionTicket(
                        sessionTicket = "SESSION-LOCAL",
                        playerId = "local-user",
                        playFabId = "PF-LOCAL",
                        playerIp = "198.51.100.10",
                        issuedAt = Instant.parse("2026-03-26T10:15:30Z"),
                        ticketPayload = """{"source":"migration-test"}""",
                    ),
                    createdAt = Instant.EPOCH,
                ),
            )
            sink.write(
                RewriteSeedBatch(
                    batchId = "service-import",
                    source = LegacyMySqlDumpDescriptor("/legacy/forum.sql", "hackwars"),
                    seedPayload = PersistedServiceSession(
                        serviceSessionId = "svc-1",
                        serviceKind = PersistedServiceKind.GAME,
                        connectionId = "game-1",
                        playerId = "local-user",
                        playFabId = "PF-LOCAL",
                        playerIp = "198.51.100.10",
                        sessionTicket = "SESSION-LOCAL",
                        authenticatedAt = Instant.parse("2026-03-26T10:16:00Z"),
                        lastSeenAt = Instant.parse("2026-03-26T10:17:00Z"),
                        sessionPayload = """{"bootstrapStateId":"computer-local"}""",
                    ),
                    createdAt = Instant.EPOCH,
                ),
            )
            sink.write(
                RewriteSeedBatch(
                    batchId = "computer-import",
                    source = LegacyXmlDescriptor("/legacy/local.xml", "computer"),
                    seedPayload = SeedComputerState(
                        computerId = "computer-local",
                        playerId = "local-user",
                        ipAddress = "198.51.100.10",
                    ),
                    createdAt = Instant.EPOCH,
                ),
            )
            sink.write(
                RewriteSeedBatch(
                    batchId = "inventory-import",
                    source = LegacyJsonDescriptor("/legacy/local-inventory.json", "inventory"),
                    seedPayload = SeedInventorySnapshot(
                        computerId = "computer-local",
                        notes = listOf("migration smoke"),
                        websiteTitle = "Migrated Website",
                        websiteBody = "<html>Smoke</html>",
                    ),
                    createdAt = Instant.EPOCH,
                ),
            )
        }

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            assertEquals(5, countRows(connection, "rewrite_import_batch"))
            assertEquals(1, countRows(connection, "rewrite_player_account"))
            assertEquals(1, countRows(connection, "rewrite_session_ticket"))
            assertEquals(1, countRows(connection, "rewrite_service_session"))
            assertEquals(1, countRows(connection, "rewrite_computer_state"))
            assertTrue(tableExists(connection, "rewrite_session_ticket"))
            assertTrue(tableExists(connection, "rewrite_service_session"))
            assertEquals("198.51.100.10", loadComputerIp(connection, "computer-local"))
        }
    }

    private fun tableExists(
        connection: java.sql.Connection,
        tableName: String,
    ): Boolean {
        connection.prepareStatement(
            """
            select count(*)
            from information_schema.tables
            where table_schema = current_schema()
              and table_name = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getInt(1) > 0
            }
        }
    }

    private fun countRows(
        connection: java.sql.Connection,
        tableName: String,
    ): Int {
        connection.prepareStatement("select count(*) from $tableName").use { statement ->
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getInt(1)
            }
        }
    }

    private fun loadComputerIp(
        connection: java.sql.Connection,
        computerId: String,
    ): String {
        connection.prepareStatement(
            """
            select ip_address
            from rewrite_computer_state
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, computerId)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getString(1)
            }
        }
    }
}
