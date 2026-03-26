package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ScriptFamily
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
class JdbcPlayerComputerProjectionRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_player_computer_projection_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun upsertsAndLoadsPlayerAndComputerProfiles() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        seedComputerState(computerId = "192.0.2.10", playerId = "local-user", ipAddress = "192.0.2.10")
        val repository = JdbcPlayerComputerProjectionRepository(connectionFactory = ::newConnection)
        val expectedProfile = PersistedPlayerProfile(
            playerId = "local-user",
            displayName = "localuser",
            profileImagePath = "images/nopic.png",
            profileDescription = "Local operator profile",
            profileLocation = "UGOPNet",
            profilePayload = """{"legacyUsername":"localuser"}""",
        )
        val expectedProjection = PersistedComputerProjection(
            computerId = "192.0.2.10",
            playerId = "local-user",
            ipAddress = "192.0.2.10",
            currentNetworkName = "UGOPNet",
            lastLoginAt = Instant.parse("2026-03-26T12:00:00Z"),
            pettyCash = 500.0,
            bankMoney = 1250.0,
            defaultBankPort = 6,
            defaultRedirectPort = 9,
            dailyPayBaseAmount = 1000.0,
            dailyPayReductionMultiplier = 0.75,
            dailyPayRevenueTargetStateId = "192.0.2.10",
            dailyPayLastPaidAtEpochMillis = 123_456L,
            totalLevel = 12,
            noobProtectionLevel = 3,
            projectionPayload = """{"websiteTitle":"Localhost"}""",
        )

        runBlocking {
            repository.upsertPlayerProfile(expectedProfile)
            repository.upsertComputerProjection(expectedProjection)
        }

        val loadedProfile = runBlocking { repository.findPlayerProfile("local-user") }
        val loadedProjection = runBlocking { repository.findComputerProjection("192.0.2.10") }

        assertNotNull(loadedProfile)
        assertNotNull(loadedProjection)
        assertEquals(normalize(expectedProfile), normalize(loadedProfile))
        assertEquals(normalize(expectedProjection), normalize(loadedProjection))
        assertNull(runBlocking { repository.findPlayerProfile("missing-user") })
        assertNull(runBlocking { repository.findComputerProjection("198.51.100.55") })
    }

    @Test
    fun replacesPreferencesAndSkillStatsDeterministically() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        seedComputerState(computerId = "192.0.2.10", playerId = "local-user", ipAddress = "192.0.2.10")
        val repository = JdbcPlayerComputerProjectionRepository(connectionFactory = ::newConnection)
        runBlocking {
            repository.upsertComputerProjection(
                PersistedComputerProjection(
                    computerId = "192.0.2.10",
                    playerId = "local-user",
                    ipAddress = "192.0.2.10",
                    currentNetworkName = "UGOPNet",
                ),
            )
        }

        runBlocking {
            repository.replacePreferences(
                computerId = "192.0.2.10",
                preferences = listOf(
                    PersistedComputerPreference("192.0.2.10", "network", "true"),
                    PersistedComputerPreference("192.0.2.10", "logwindow", "false"),
                ),
            )
            repository.replaceSkillStats(
                computerId = "192.0.2.10",
                stats = listOf(
                    PersistedComputerSkillStat("192.0.2.10", ScriptFamily.ATTACK, 25.0),
                    PersistedComputerSkillStat("192.0.2.10", ScriptFamily.WATCH, 12.5),
                ),
            )
        }

        assertEquals(
            listOf(
                PersistedComputerPreference("192.0.2.10", "logwindow", "false"),
                PersistedComputerPreference("192.0.2.10", "network", "true"),
            ),
            runBlocking { repository.listPreferences("192.0.2.10") },
        )
        assertEquals(
            listOf(
                PersistedComputerSkillStat("192.0.2.10", ScriptFamily.ATTACK, 25.0),
                PersistedComputerSkillStat("192.0.2.10", ScriptFamily.WATCH, 12.5),
            ),
            runBlocking { repository.listSkillStats("192.0.2.10") },
        )

        runBlocking {
            repository.replacePreferences(
                computerId = "192.0.2.10",
                preferences = listOf(
                    PersistedComputerPreference("192.0.2.10", "network", "false"),
                ),
            )
            repository.replaceSkillStats(
                computerId = "192.0.2.10",
                stats = listOf(
                    PersistedComputerSkillStat("192.0.2.10", ScriptFamily.HTTP, 88.0),
                ),
            )
        }

        assertEquals(
            listOf(PersistedComputerPreference("192.0.2.10", "network", "false")),
            runBlocking { repository.listPreferences("192.0.2.10") },
        )
        assertEquals(
            listOf(PersistedComputerSkillStat("192.0.2.10", ScriptFamily.HTTP, 88.0)),
            runBlocking { repository.listSkillStats("192.0.2.10") },
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

    private fun seedComputerState(
        computerId: String,
        playerId: String,
        ipAddress: String,
    ) {
        newConnection().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_computer_state(computer_id, player_id, ip_address, state_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, computerId)
                statement.setString(2, playerId)
                statement.setString(3, ipAddress)
                statement.setString(
                    4,
                    """{"id":"$computerId","identity":{"playerIp":"$ipAddress"}}""",
                )
                statement.executeUpdate()
            }
        }
    }

    private fun normalize(profile: PersistedPlayerProfile): PersistedPlayerProfile {
        return profile.copy(profilePayload = canonicalJson(profile.profilePayload))
    }

    private fun normalize(projection: PersistedComputerProjection): PersistedComputerProjection {
        return projection.copy(projectionPayload = canonicalJson(projection.projectionPayload))
    }

    private fun canonicalJson(raw: String): String {
        return Json.parseToJsonElement(raw).toString()
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
