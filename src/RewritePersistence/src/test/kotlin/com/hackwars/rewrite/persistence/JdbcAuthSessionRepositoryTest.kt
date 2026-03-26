package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
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
class JdbcAuthSessionRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_auth_session_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun upsertsAndLoadsCanonicalSessionTickets() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        val repository = JdbcAuthSessionRepository(connectionFactory = ::newConnection)
        val issuedAt = Instant.parse("2026-03-26T10:15:30Z")
        val expiresAt = Instant.parse("2026-03-26T11:15:30Z")
        val expected = PersistedSessionTicket(
            sessionTicket = "SESSION-LOCAL",
            playerId = "local-user",
            playFabId = "PF-LOCAL",
            playerIp = "192.0.2.10",
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            ticketPayload = """{"source":"seed","service":"GAME"}""",
        )

        runBlocking { repository.upsertSessionTicket(expected) }

        val loaded = runBlocking { repository.findSessionTicket("SESSION-LOCAL") }

        assertNotNull(loaded)
        assertEquals(normalize(expected), normalize(loaded))
        assertNull(runBlocking { repository.findSessionTicket("SESSION-UNKNOWN") })
    }

    @Test
    fun upsertsTouchesClosesAndListsServiceSessions() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        val repository = JdbcAuthSessionRepository(connectionFactory = ::newConnection)
        runBlocking {
            repository.upsertSessionTicket(
                PersistedSessionTicket(
                    sessionTicket = "SESSION-LOCAL",
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "192.0.2.10",
                    issuedAt = Instant.parse("2026-03-26T10:15:30Z"),
                    ticketPayload = """{"source":"auth"}""",
                ),
            )
        }
        val startedAt = Instant.parse("2026-03-26T10:16:00Z")
        val updatedSeenAt = Instant.parse("2026-03-26T10:17:00Z")
        val closedAt = Instant.parse("2026-03-26T10:18:00Z")
        val repositorySession = PersistedServiceSession(
            serviceSessionId = "svc-1",
            serviceKind = PersistedServiceKind.GAME,
            connectionId = "game-1",
            playerId = "local-user",
            playFabId = "PF-LOCAL",
            playerIp = "192.0.2.10",
            sessionTicket = "SESSION-LOCAL",
            clientBuild = "rewrite-dev",
            heartbeatIntervalMillis = 15_000L,
            authenticatedAt = startedAt,
            lastSeenAt = startedAt,
            sessionPayload = """{"bootstrapStateId":"192.0.2.10"}""",
        )

        runBlocking { repository.upsertServiceSession(repositorySession) }

        val started = runBlocking {
            repository.findServiceSession(PersistedServiceKind.GAME, "game-1")
        }
        assertNotNull(started)
        assertEquals(normalize(repositorySession), normalize(started))

        runBlocking {
            repository.touchServiceSession(
                serviceKind = PersistedServiceKind.GAME,
                connectionId = "game-1",
                lastSeenAt = updatedSeenAt,
            )
        }
        val touched = runBlocking {
            repository.findServiceSession(PersistedServiceKind.GAME, "game-1")
        }
        assertNotNull(touched)
        assertEquals(updatedSeenAt, touched.lastSeenAt)

        val activeBeforeClose = runBlocking { repository.listActiveServiceSessions("local-user") }
        assertEquals(listOf(touched), activeBeforeClose)

        runBlocking {
            repository.closeServiceSession(
                serviceKind = PersistedServiceKind.GAME,
                connectionId = "game-1",
                closedAt = closedAt,
            )
        }
        val closed = runBlocking {
            repository.findServiceSession(PersistedServiceKind.GAME, "game-1")
        }
        assertNotNull(closed)
        assertEquals(closedAt, closed.closedAt)
        assertEquals(closedAt, closed.lastSeenAt)
        assertEquals(emptyList(), runBlocking { repository.listActiveServiceSessions("local-user") })
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

    private fun seedPlayerAccount(
        playerId: String,
        playFabId: String,
        playerIp: String,
    ) {
        newConnection().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_player_account(player_id, playfab_id, player_ip, account_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, playFabId)
                statement.setString(3, playerIp)
                statement.setString(
                    4,
                    """{"playerId":"$playerId","playFabId":"$playFabId","playerIp":"$playerIp"}""",
                )
                statement.executeUpdate()
            }
        }
    }

    private fun canonicalJson(raw: String): String {
        return Json.parseToJsonElement(raw).toString()
    }

    private fun normalize(ticket: PersistedSessionTicket): PersistedSessionTicket {
        return ticket.copy(ticketPayload = canonicalJson(ticket.ticketPayload))
    }

    private fun normalize(session: PersistedServiceSession): PersistedServiceSession {
        return session.copy(sessionPayload = canonicalJson(session.sessionPayload))
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
