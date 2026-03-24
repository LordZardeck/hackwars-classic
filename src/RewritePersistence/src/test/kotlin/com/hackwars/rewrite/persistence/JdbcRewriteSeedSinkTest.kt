package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class JdbcRewriteSeedSinkTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_seed_sink_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val sink = JdbcRewriteSeedSink(connectionFactory = ::newConnection)
    private val serializer = ComputerStateSerializer()

    @Test
    fun importerSmokeLoadsSeededAccountComputerAndInventorySlice() {
        resetDatabase()

        writeBatch(
            RewriteSeedBatch(
                batchId = "player-1",
                source = LegacyMySqlDumpDescriptor("/legacy/hackwars.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "local-user",
                    playFabId = "PF-LOCALUSER",
                    playerIp = "LOCAL-IP",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-1",
                source = LegacyXmlDescriptor("/legacy/hackwars.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "LOCAL-IP",
                    playerId = "local-user",
                    ipAddress = "LOCAL-IP",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-1",
                source = LegacyJsonDescriptor("/legacy/inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "LOCAL-IP",
                    notes = listOf("migration note"),
                ),
                createdAt = Instant.EPOCH,
            ),
        )

        assertEquals(3, countRows("rewrite_import_batch"))
        assertEquals(1, countRows("rewrite_player_account"))
        assertEquals(1, countRows("rewrite_computer_state"))

        val state = loadStatePayload("LOCAL-IP")
        assertTrue(state.filesystem.files.any { it.name == "note-1.txt" && it.contents == "migration note" })
    }

    private fun resetDatabase() {
        newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop schema if exists public cascade")
                statement.execute("create schema public")
            }
            RewriteLiquibase.update(connection)
        }
    }

    private fun writeBatch(batch: RewriteSeedBatch) {
        kotlinx.coroutines.runBlocking {
            sink.write(batch)
        }
    }

    private fun loadStatePayload(computerId: String) = newConnection().use { connection ->
        connection.prepareStatement(
            """
            select state_payload::text
            from rewrite_computer_state
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, computerId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                serializer.decodeStateJson(resultSet.getString(1))
            }
        }
    }

    private fun countRows(tableName: String): Int = newConnection().use { connection ->
        connection.prepareStatement("select count(*) from $tableName").use { statement ->
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
