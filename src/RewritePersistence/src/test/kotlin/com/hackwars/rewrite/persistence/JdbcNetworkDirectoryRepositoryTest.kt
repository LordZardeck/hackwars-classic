package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.JAIL_NETWORK_NAME
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
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
class JdbcNetworkDirectoryRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_network_directory_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val sink = JdbcRewriteSeedSink(connectionFactory = ::newConnection)

    @Test
    fun loadsKnownNetworkWithStoreResolutionAndNpcCategories() {
        resetDatabase()
        writeBatch(worldDirectoryBatch())
        val repository = JdbcNetworkDirectoryRepository(connectionFactory = ::newConnection)

        val definition = runBlocking { repository.loadNetwork("ProgNet") }

        requireNotNull(definition)
        assertEquals(GameStateId("store1"), definition.storeStateId)
        assertEquals("Prog Courier", definition.regularNpcs.single().displayName)
        assertEquals("Prog Mentor", definition.questNpcs.single().displayName)
        assertEquals("Prog Quarry", definition.miningNpcs.single().displayName)
        assertEquals("Shard Store", definition.storeNpcs.single().displayName)
    }

    @Test
    fun validatesSwitchesUsingPersistedLinkRowsAndAllowedNetworks() {
        resetDatabase()
        writeBatch(worldDirectoryBatch())
        val repository = JdbcNetworkDirectoryRepository(connectionFactory = ::newConnection)

        val blocked = runBlocking {
            repository.validateSwitch(
                fromNetwork = ROOT_NETWORK_NAME,
                toNetwork = "ProgNet",
                allowedNetworks = emptySet(),
            )
        }
        val allowed = runBlocking {
            repository.validateSwitch(
                fromNetwork = ROOT_NETWORK_NAME,
                toNetwork = "ProgNet",
                allowedNetworks = setOf("ProgNet"),
            )
        }
        val missing = runBlocking {
            repository.validateSwitch(
                fromNetwork = ROOT_NETWORK_NAME,
                toNetwork = "UnknownNet",
                allowedNetworks = emptySet(),
            )
        }

        assertFalse(blocked.allowed)
        assertEquals("A gateway to ProgNet is currently locked.", blocked.failureMessage)
        assertTrue(allowed.allowed)
        assertEquals("", allowed.failureMessage)
        assertFalse(missing.allowed)
        assertEquals("There is no connection between UGOPNet and UnknownNet.", missing.failureMessage)
    }

    private fun worldDirectoryBatch(): RewriteSeedBatch {
        return RewriteSeedBatch(
            batchId = "world-1",
            source = LegacyJsonDescriptor("/legacy/world.json", "world"),
            seedPayload = SeedWorldDirectory(
                networks = listOf(
                    SeedWorldNetworkDefinition(
                        name = ROOT_NETWORK_NAME,
                        storeStateId = "store1",
                        attachedNetworks = listOf(
                            SeedAttachedNetworkLink(
                                targetNetworkName = "ProgNet",
                                entranceMessage = "UGOPNet uplink engaged.",
                                failureMessage = "A gateway to ProgNet is currently locked.",
                            ),
                        ),
                        npcs = listOf(
                            SeedWorldNpcEntry("UGOP-ATTACK-1", "Root Hunter", "Attack NPC", NpcCategory.REGULAR),
                            SeedWorldNpcEntry("UGOP-QUEST-1", "Quest Guide", "Quest NPC", NpcCategory.QUEST),
                            SeedWorldNpcEntry("UGOP-MINE-1", "Root Miner", "Mining NPC", NpcCategory.MINING, "Silicon"),
                            SeedWorldNpcEntry("store1", "Shard Store", "Store NPC", NpcCategory.STORE),
                        ),
                    ),
                    SeedWorldNetworkDefinition(
                        name = "ProgNet",
                        storeStateId = "store1",
                        attachedNetworks = listOf(
                            SeedAttachedNetworkLink(
                                targetNetworkName = ROOT_NETWORK_NAME,
                                entranceMessage = "ProgNet relay engaged.",
                                failureMessage = "The uplink back to UGOPNet is unstable.",
                            ),
                        ),
                        npcs = listOf(
                            SeedWorldNpcEntry("PROG-ATTACK-1", "Prog Courier", "Attack NPC", NpcCategory.REGULAR),
                            SeedWorldNpcEntry("PROG-QUEST-1", "Prog Mentor", "Quest NPC", NpcCategory.QUEST),
                            SeedWorldNpcEntry("PROG-MINE-1", "Prog Quarry", "Mining NPC", NpcCategory.MINING, "Germanium"),
                            SeedWorldNpcEntry("store1", "Shard Store", "Store NPC", NpcCategory.STORE),
                        ),
                    ),
                    SeedWorldNetworkDefinition(
                        name = JAIL_NETWORK_NAME,
                        storeStateId = null,
                    ),
                ),
            ),
            createdAt = Instant.EPOCH,
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
