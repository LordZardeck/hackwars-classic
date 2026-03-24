package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.PreferenceSetEvent
import com.hackwars.rewrite.gamecore.SnapshotCoordinator
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class JdbcComputerStateRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_state_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val serializer = ComputerStateSerializer()

    @Test
    fun appendsTypedEventsAndReplaysDeterministicState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
        )

        val updated = runBlockingAppend(repository, stateId, listOf(
            PreferenceSetEvent("show_clock", "true"),
            PreferenceSetEvent("show_logs", "false"),
        ))
        val reloaded = runBlockingLoad(repository, stateId)

        assertEquals(updated, reloaded)
        assertEquals(2, updated.version)
        assertEquals("true", updated.preferences.values["show_clock"])
        assertEquals("false", updated.preferences.values["show_logs"])
        assertEquals(2, countRows("rewrite_state_event"))
    }

    @Test
    fun snapshotsAfterEventOrTimeThresholds() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        var now = Instant.parse("2026-03-24T12:00:00Z")
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(
                eventThreshold = 2,
                timeThreshold = 5.seconds,
            ),
            clock = { now },
        )

        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_clock", "true")))
        assertEquals(0, countRows("rewrite_state_snapshot"))

        now = now.plusSeconds(6)
        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_logs", "false")))
        assertEquals(1, countRows("rewrite_state_snapshot"))
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

    private fun seedPlayerAndComputer(
        stateId: GameStateId,
        state: ComputerState,
        playerId: String = "local-user",
    ) {
        newConnection().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_player_account(player_id, playfab_id, player_ip, account_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" })
                statement.setString(3, state.identity.playerIp.ifBlank { stateId.value })
                statement.setString(
                    4,
                    """{"playerId":"$playerId","playFabId":"${state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" }}","playerIp":"${state.identity.playerIp.ifBlank { stateId.value }}"}""",
                )
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into rewrite_computer_state(computer_id, player_id, ip_address, state_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, stateId.value)
                statement.setString(2, playerId)
                statement.setString(3, stateId.value)
                statement.setString(4, serializer.encodeStateJson(state))
                statement.executeUpdate()
            }
        }
    }

    private fun countRows(tableName: String): Int {
        newConnection().use { connection ->
            connection.prepareStatement("select count(*) from $tableName").use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt(1)
                }
            }
        }
    }

    private fun runBlockingAppend(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
        events: List<PreferenceSetEvent>,
    ): ComputerState {
        return kotlinx.coroutines.runBlocking {
            repository.appendEvents(stateId, events)
        }
    }

    private fun runBlockingLoad(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
    ): ComputerState? {
        return kotlinx.coroutines.runBlocking {
            repository.load(stateId)
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
