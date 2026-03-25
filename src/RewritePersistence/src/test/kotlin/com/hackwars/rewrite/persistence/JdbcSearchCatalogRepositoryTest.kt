package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
class JdbcSearchCatalogRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_search_catalog_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val sink = JdbcRewriteSeedSink(connectionFactory = ::newConnection)

    @Test
    fun loadsSearchDocumentsFromPersistedComputerStateWithVisibilityFields() {
        resetDatabase()
        seedSearchState(
            batchId = "active",
            playerId = "active-user",
            playFabId = "PF-ACTIVE",
            ip = "ACTIVE-IP",
            title = "Active Site",
            body = "<html>alpha beta</html>",
            isNpc = false,
            lastLoginAtEpochMillis = 1_000L,
            enableHttp = true,
        )
        seedSearchState(
            batchId = "inactive",
            playerId = "inactive-user",
            playFabId = "PF-INACTIVE",
            ip = "INACTIVE-IP",
            title = "Inactive Site",
            body = "<html>alpha gamma</html>",
            isNpc = false,
            lastLoginAtEpochMillis = 2_000L,
            enableHttp = true,
        )
        seedSearchState(
            batchId = "npc",
            playerId = "npc-user",
            playFabId = "PF-NPC",
            ip = "NPC-IP",
            title = "NPC Site",
            body = "<html>npc alpha</html>",
            isNpc = true,
            lastLoginAtEpochMillis = null,
            enableHttp = true,
        )
        seedSearchState(
            batchId = "no-http",
            playerId = "no-http-user",
            playFabId = "PF-NOHTTP",
            ip = "NO-HTTP-IP",
            title = "No Http Site",
            body = "<html>alpha hidden</html>",
            isNpc = false,
            lastLoginAtEpochMillis = 3_000L,
            enableHttp = false,
        )

        val repository = JdbcSearchCatalogRepository(connectionFactory = ::newConnection)
        val documents = runBlocking { repository.loadDocuments() }
        val byAddress = documents.associateBy { it.address }

        assertEquals(4, documents.size)
        assertTrue(byAddress.getValue("active-ip").searchable)
        assertEquals(1_000L, byAddress.getValue("active-ip").lastLoginAtEpochMillis)
        assertTrue(byAddress.getValue("inactive-ip").searchable)
        assertEquals("Inactive Site", byAddress.getValue("inactive-ip").title)
        assertTrue(byAddress.getValue("npc-ip").isNpc)
        assertTrue(byAddress.getValue("npc-ip").searchable)
        assertFalse(byAddress.getValue("no-http-ip").searchable)
    }

    private fun seedSearchState(
        batchId: String,
        playerId: String,
        playFabId: String,
        ip: String,
        title: String,
        body: String,
        isNpc: Boolean,
        lastLoginAtEpochMillis: Long?,
        enableHttp: Boolean,
    ) {
        writeBatch(
            RewriteSeedBatch(
                batchId = "$batchId-player",
                source = LegacyMySqlDumpDescriptor("/legacy/$batchId.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = playerId,
                    playFabId = playFabId,
                    playerIp = ip,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "$batchId-computer",
                source = LegacyXmlDescriptor("/legacy/$batchId.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = ip,
                    playerId = playerId,
                    ipAddress = ip,
                    isNpc = isNpc,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "$batchId-inventory",
                source = LegacyJsonDescriptor("/legacy/$batchId-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = ip,
                    notes = listOf("search fixture"),
                    websiteTitle = title,
                    websiteBody = body,
                    lastLoginAtEpochMillis = lastLoginAtEpochMillis,
                    enableHttp = enableHttp,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
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
        runBlocking {
            sink.write(batch)
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
