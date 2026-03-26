package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
class JdbcWebsiteProjectionRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_website_projection_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun upsertsAndLoadsWebsiteProjectionsWithoutComputerForeignKeys() {
        resetDatabase()
        val repository = JdbcWebsiteProjectionRepository(connectionFactory = ::newConnection)
        val expected = PersistedWebsiteProjection(
            stateId = "198.51.100.44",
            canonicalAddress = "198.51.100.44",
            title = "Detached Website",
            bodyHtml = "<html>Detached</html>",
            voteCount = 5,
            votesAvailable = 2,
            storeRevenueTargetStateId = "198.51.100.45",
            websitePayload = """{"legacy":"website"}""",
        )

        runBlocking { repository.upsertWebsiteProjection(expected) }

        val loaded = runBlocking { repository.findWebsiteProjection("198.51.100.44") }

        assertNotNull(loaded)
        assertEquals(normalize(expected), normalize(loaded))
        assertNull(runBlocking { repository.findWebsiteProjection("203.0.113.99") })
    }

    @Test
    fun listsWebsiteProjectionsInAddressOrder() {
        resetDatabase()
        val repository = JdbcWebsiteProjectionRepository(connectionFactory = ::newConnection)
        runBlocking {
            repository.upsertWebsiteProjection(
                PersistedWebsiteProjection(
                    stateId = "203.0.113.20",
                    canonicalAddress = "203.0.113.20",
                    title = "Zulu",
                    bodyHtml = "<html>Zulu</html>",
                    websitePayload = """{"rank":2}""",
                ),
            )
            repository.upsertWebsiteProjection(
                PersistedWebsiteProjection(
                    stateId = "198.51.100.10",
                    canonicalAddress = "198.51.100.10",
                    title = "Alpha",
                    bodyHtml = "<html>Alpha</html>",
                    websitePayload = """{"rank":1}""",
                ),
            )
        }

        val projections = runBlocking { repository.listWebsiteProjections() }

        assertEquals(listOf("198.51.100.10", "203.0.113.20"), projections.map { it.canonicalAddress })
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

    private fun normalize(projection: PersistedWebsiteProjection): PersistedWebsiteProjection {
        return projection.copy(websitePayload = canonicalJson(projection.websitePayload))
    }

    private fun canonicalJson(raw: String): String {
        return Json.parseToJsonElement(raw).toString()
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
