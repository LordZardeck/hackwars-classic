package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.GameStateId
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers
class JdbcFtpPasswordRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_ftp_password_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun savesLoadsAndClearsFtpPasswordsWithoutLeakingEmptyValues() {
        resetDatabase()
        seedPlayerAccount(playerId = "local-user", playFabId = "PF-LOCAL", playerIp = "192.0.2.10")
        seedComputerState(computerId = "192.0.2.10", playerId = "local-user", ipAddress = "192.0.2.10")
        seedComputerProjection(computerId = "192.0.2.10", playerId = "local-user", ipAddress = "192.0.2.10")
        val repository = JdbcFtpPasswordRepository(connectionFactory = ::newConnection)
        val stateId = GameStateId("192.0.2.10")

        assertNull(runBlocking { repository.load(stateId) })

        runBlocking {
            repository.save(stateId, "vault")
        }

        assertEquals("vault", runBlocking { repository.load(stateId) })
        assertEquals(
            "vault",
            storedPreferenceValue(computerId = "192.0.2.10", key = "__ftp_password"),
        )

        runBlocking {
            repository.save(stateId, "")
        }

        assertNull(runBlocking { repository.load(stateId) })
        assertNull(storedPreferenceValue(computerId = "192.0.2.10", key = "__ftp_password"))
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

    private fun seedComputerProjection(
        computerId: String,
        playerId: String,
        ipAddress: String,
    ) {
        runBlocking {
            JdbcPlayerComputerProjectionRepository(connectionFactory = ::newConnection)
                .upsertComputerProjection(
                    PersistedComputerProjection(
                        computerId = computerId,
                        playerId = playerId,
                        ipAddress = ipAddress,
                        currentNetworkName = "UGOPNet",
                        projectionPayload = """{"playerIp":"$ipAddress"}""",
                    ),
                )
        }
    }

    private fun storedPreferenceValue(
        computerId: String,
        key: String,
    ): String? {
        return newConnection().use { connection ->
            connection.prepareStatement(
                """
                select preference_value
                from rewrite_computer_preference
                where computer_id = ?
                  and preference_key = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, computerId)
                statement.setString(2, key)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return resultSet.getString("preference_value")
                }
            }
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
