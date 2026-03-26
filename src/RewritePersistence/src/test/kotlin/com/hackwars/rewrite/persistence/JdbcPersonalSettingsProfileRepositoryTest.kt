package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.PersonalSettingsProfile

@Testcontainers
class JdbcPersonalSettingsProfileRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_personal_settings_profile_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun loadsAndSavesProfilesByComputerId() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        seedComputerState(computerId = "192.0.2.10", playerId = "local-user", ipAddress = "192.0.2.10")

        val repository = JdbcPersonalSettingsProfileRepository(connectionFactory = ::newConnection)

        assertNull(runBlocking { repository.load(GameStateId("192.0.2.10")) })

        runBlocking {
            repository.save(
                stateId = GameStateId("192.0.2.10"),
                profile = PersonalSettingsProfile(
                    displayName = "localuser",
                    imagePath = "images/Gunner001.png",
                    description = "Local operator profile",
                    location = "UGOPNet",
                ),
            )
        }

        val loaded = runBlocking { repository.load(GameStateId("192.0.2.10")) }
        assertNotNull(loaded)
        assertEquals(
            PersonalSettingsProfile(
                displayName = "localuser",
                imagePath = "images/Gunner001.png",
                description = "Local operator profile",
                location = "UGOPNet",
            ),
            loaded,
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

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
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
                statement.setString(4, """{"legacyUsername":"$playerId"}""")
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
        runBlocking {
            JdbcPlayerComputerProjectionRepository(connectionFactory = ::newConnection)
                .upsertComputerProjection(
                    PersistedComputerProjection(
                        computerId = computerId,
                        playerId = playerId,
                        ipAddress = ipAddress,
                    ),
                )
        }
    }
}
