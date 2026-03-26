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
import java.sql.SQLException

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
            assertTableExists(it, "rewrite_session_ticket")
            assertTableExists(it, "rewrite_service_session")
            assertTableExists(it, "rewrite_chat_channel")
            assertTableExists(it, "rewrite_chat_channel_member")
            assertTableExists(it, "rewrite_chat_message")
            assertTableExists(it, "rewrite_chat_relation")
            assertTableExists(it, "rewrite_chat_channel_mute")
            assertTableExists(it, "rewrite_chat_presence")
            assertTableExists(it, "rewrite_player_profile")
            assertTableExists(it, "rewrite_computer_projection")
            assertTableExists(it, "rewrite_computer_preference")
            assertTableExists(it, "rewrite_computer_skill_stat")
            assertTableExists(it, "rewrite_website_projection")
        }
    }

    @Test
    fun rollbackRemovesBootstrapTables() {
        withConnection {
            RewriteLiquibase.update(it)
            RewriteLiquibase.rollback(it, 6)

            assertTableMissing(it, "rewrite_import_batch")
            assertTableMissing(it, "rewrite_player_account")
            assertTableMissing(it, "rewrite_computer_state")
            assertTableMissing(it, "rewrite_state_event")
            assertTableMissing(it, "rewrite_state_snapshot")
            assertTableMissing(it, "rewrite_network_directory")
            assertTableMissing(it, "rewrite_network_link")
            assertTableMissing(it, "rewrite_network_npc")
            assertTableMissing(it, "rewrite_session_ticket")
            assertTableMissing(it, "rewrite_service_session")
            assertTableMissing(it, "rewrite_chat_channel")
            assertTableMissing(it, "rewrite_chat_channel_member")
            assertTableMissing(it, "rewrite_chat_message")
            assertTableMissing(it, "rewrite_chat_relation")
            assertTableMissing(it, "rewrite_chat_channel_mute")
            assertTableMissing(it, "rewrite_chat_presence")
            assertTableMissing(it, "rewrite_player_profile")
            assertTableMissing(it, "rewrite_computer_projection")
            assertTableMissing(it, "rewrite_computer_preference")
            assertTableMissing(it, "rewrite_computer_skill_stat")
            assertTableMissing(it, "rewrite_website_projection")
        }
    }

    @Test
    fun updateCreatesRetainedWorldWebsiteSliceColumnsAndConstraints() {
        withConnection { connection ->
            RewriteLiquibase.update(connection)
            connection.autoCommit = false

            listOf(
                "state_id",
                "canonical_address",
                "title",
                "body_html",
                "vote_count",
                "votes_available",
                "store_revenue_target_state_id",
                "website_payload",
            ).forEach { columnName ->
                assertTrue(
                    columnExists(connection, "rewrite_website_projection", columnName),
                    "Expected rewrite_website_projection.$columnName to exist",
                )
            }

            assertEquals(
                7,
                countNamedConstraints(
                    connection,
                    listOf(
                        "pk_rewrite_website_projection",
                        "uq_rewrite_website_projection_address",
                        "ck_rewrite_website_projection_vote_count_nonnegative",
                        "ck_rewrite_website_projection_votes_available_nonnegative",
                        "ck_rewrite_network_npc_category",
                        "ck_rewrite_network_npc_sort_nonnegative",
                        "uq_rewrite_network_npc_category_sort",
                    ),
                ),
            )

            assertDoesNotThrow {
                connection.prepareStatement(
                    """
                    insert into rewrite_website_projection(
                        state_id,
                        canonical_address,
                        title,
                        body_html,
                        vote_count,
                        votes_available,
                        store_revenue_target_state_id,
                        website_payload
                    )
                    values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "198.51.100.44")
                    statement.setString(2, "198.51.100.44")
                    statement.setString(3, "Detached Website")
                    statement.setString(4, "<html>Detached</html>")
                    statement.setInt(5, 2)
                    statement.setInt(6, 1)
                    statement.setString(7, null)
                    statement.setString(8, """{"seed":"detached"}""")
                    statement.executeUpdate()
                }
            }

            connection.prepareStatement(
                """
                insert into rewrite_network_directory(network_name, store_state_id)
                values ('TestNet', null)
                """.trimIndent(),
            ).use { it.executeUpdate() }

            assertStatementFails(connection) {
                connection.prepareStatement(
                    """
                    insert into rewrite_network_npc(
                        network_name,
                        state_id,
                        display_name,
                        title,
                        category,
                        commodity,
                        sort_order
                    )
                    values ('TestNet', 'NPC-INVALID', 'Invalid', '', 'BAD', null, 0)
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            connection.prepareStatement(
                """
                insert into rewrite_network_npc(
                    network_name,
                    state_id,
                    display_name,
                    title,
                    category,
                    commodity,
                    sort_order
                )
                values ('TestNet', 'NPC-ONE', 'One', '', 'REGULAR', null, 0)
                """.trimIndent(),
            ).use { it.executeUpdate() }

            assertStatementFails(connection) {
                connection.prepareStatement(
                    """
                    insert into rewrite_network_npc(
                        network_name,
                        state_id,
                        display_name,
                        title,
                        category,
                        commodity,
                        sort_order
                    )
                    values ('TestNet', 'NPC-TWO', 'Two', '', 'REGULAR', null, 0)
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            assertStatementFails(connection) {
                connection.prepareStatement(
                    """
                    insert into rewrite_website_projection(
                        state_id,
                        canonical_address,
                        title,
                        body_html,
                        vote_count,
                        votes_available,
                        store_revenue_target_state_id,
                        website_payload
                    )
                    values ('198.51.100.45', '198.51.100.45', 'Negative Votes', '<html>x</html>', -1, 0, null, cast('{}' as jsonb))
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            assertStatementFails(connection) {
                connection.prepareStatement(
                    """
                    insert into rewrite_website_projection(
                        state_id,
                        canonical_address,
                        title,
                        body_html,
                        vote_count,
                        votes_available,
                        store_revenue_target_state_id,
                        website_payload
                    )
                    values ('198.51.100.46', '198.51.100.46', 'Negative Available', '<html>x</html>', 0, -1, null, cast('{}' as jsonb))
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            connection.rollback()
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
        val worldBatch = coordinator.plan(
            LegacyJsonDescriptor(
                sourceLocation = "/legacy/world.json",
                documentType = "world",
            )
        )
        val chatSocialBatch = coordinator.plan(
            LegacyJsonDescriptor(
                sourceLocation = "/legacy/chat-social.json",
                documentType = "chat-social",
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

        assertEquals("json:/legacy/world.json", worldBatch.batchId)
        assertTrue(worldBatch.seedPayload is SeedWorldDirectory)

        assertEquals("json:/legacy/chat-social.json", chatSocialBatch.batchId)
        assertTrue(chatSocialBatch.seedPayload is SeedChatSocialSnapshot)
    }

    @Test
    fun importerPlannerRejectsUnknownJsonDocumentTypes() {
        val coordinator = LegacyImportCoordinator()

        val failure = assertThrows<IllegalArgumentException> {
            coordinator.plan(
                LegacyJsonDescriptor(
                    sourceLocation = "/legacy/unsupported.json",
                    documentType = "economy",
                )
            )
        }

        assertEquals("Unsupported legacy JSON document type: economy", failure.message)
    }

    @Test
    fun seedSinkRecordsBatchesForLaterDatabaseWriting() {
        val sink = SeedBatchRecorder()
        val batch = LegacyImportCoordinator().plan(
            LegacyJsonDescriptor(
                sourceLocation = "/legacy/seed.json",
                documentType = "inventory",
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

    private fun columnExists(
        connection: java.sql.Connection,
        tableName: String,
        columnName: String,
    ): Boolean {
        connection.prepareStatement(
            """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = ?
              and column_name = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.setString(2, columnName)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getInt(1) > 0
            }
        }
    }

    private fun countNamedConstraints(
        connection: java.sql.Connection,
        constraintNames: List<String>,
    ): Int {
        connection.prepareStatement(
            """
            select count(*)
            from pg_constraint
            where connamespace = current_schema()::regnamespace
              and conname = any (?)
            """.trimIndent(),
        ).use { statement ->
            statement.setArray(1, connection.createArrayOf("varchar", constraintNames.toTypedArray()))
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                return resultSet.getInt(1)
            }
        }
    }

    private fun assertStatementFails(
        connection: java.sql.Connection,
        action: () -> Unit,
    ) {
        val savepoint = connection.setSavepoint()
        assertThrows<SQLException> {
            action()
        }
        connection.rollback(savepoint)
        connection.releaseSavepoint(savepoint)
    }
}
