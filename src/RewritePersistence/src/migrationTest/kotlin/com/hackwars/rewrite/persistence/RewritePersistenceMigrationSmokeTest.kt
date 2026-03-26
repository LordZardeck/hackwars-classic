package com.hackwars.rewrite.persistence

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
}
