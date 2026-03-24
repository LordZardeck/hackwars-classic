package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ProgramScriptSlot
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
                    websiteTitle = "Seeded Local Page",
                    websiteBody = "<html>Seeded Local Body</html>",
                    votesAvailable = 2,
                    voteCount = 4,
                    totalLevel = 5,
                    noobProtectionLevel = 3,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "player-store",
                source = LegacyMySqlDumpDescriptor("/legacy/store.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "store-user",
                    playFabId = "PF-STORE",
                    playerIp = "store1",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-store",
                source = LegacyXmlDescriptor("/legacy/store.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "store1",
                    playerId = "store-user",
                    ipAddress = "store1",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-store",
                source = LegacyJsonDescriptor("/legacy/store-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "store1",
                    notes = listOf("shard store note"),
                    websiteTitle = "Canonical Store",
                    websiteBody = "<html>Store Body</html>",
                    voteCount = 9,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "player-offline",
                source = LegacyMySqlDumpDescriptor("/legacy/offline.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "offline-user",
                    playFabId = "PF-OFFLINE",
                    playerIp = "offline1",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-offline",
                source = LegacyXmlDescriptor("/legacy/offline.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "offline1",
                    playerId = "offline-user",
                    ipAddress = "offline1",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-offline",
                source = LegacyJsonDescriptor("/legacy/offline-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "offline1",
                    notes = listOf("offline note"),
                    websiteTitle = "Offline Page",
                    websiteBody = "<html>Offline Body</html>",
                    enableHttp = false,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "player-npc",
                source = LegacyMySqlDumpDescriptor("/legacy/npc.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "npc-user",
                    playFabId = "PF-NPC",
                    playerIp = "NPC-IP",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-npc",
                source = LegacyXmlDescriptor("/legacy/npc.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "NPC-IP",
                    playerId = "npc-user",
                    ipAddress = "NPC-IP",
                    isNpc = true,
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-npc",
                source = LegacyJsonDescriptor("/legacy/npc-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "NPC-IP",
                    notes = listOf("npc note"),
                    websiteTitle = "NPC Page",
                    websiteBody = "<html>NPC Body</html>",
                ),
                createdAt = Instant.EPOCH,
            ),
        )

        assertEquals(12, countRows("rewrite_import_batch"))
        assertEquals(4, countRows("rewrite_player_account"))
        assertEquals(4, countRows("rewrite_computer_state"))

        val state = loadStatePayload("LOCAL-IP")
        val storeState = loadStatePayload("store1")
        val offlineState = loadStatePayload("offline1")
        val npcState = loadStatePayload("NPC-IP")
        assertTrue(state.filesystem.directoriesByPath.containsKey("/Public"))
        assertTrue(state.filesystem.directoriesByPath.containsKey("/Store"))
        assertTrue(state.filesystem.filesByPath.values.any { it.name == "note-1.txt" && it.contents == "migration note" })
        assertTrue(state.filesystem.filesByPath.containsKey("/readme.txt"))
        assertTrue(state.filesystem.filesByPath.containsKey("/Store/catalog.txt"))
        assertTrue(state.filesystem.filesByPath["/Public/bank.bin"]?.compiledBinary?.bankingApplication == true)
        assertTrue(state.filesystem.filesByPath["/Public/http"]?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true)
        assertTrue(state.filesystem.filesByPath["/Public/http.bin"]?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true)
        assertTrue(state.filesystem.filesByPath["/Public/http"]?.scriptBundle?.script(ProgramScriptSlot.ENTER)?.contains("logMessage") == true)
        assertEquals("Seeded Local Page", state.website.title)
        assertEquals(2, state.website.votesAvailable)
        assertEquals(4, state.website.voteCount)
        assertEquals(6, state.economy.defaultBankPort)
        assertTrue(state.ports.any { it.number == 80 && it.installedApplication?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true })
        assertTrue(state.ports.any { it.number == 80 && it.defaultPort })
        assertTrue(storeState.filesystem.directoriesByPath.containsKey("/Store"))
        assertTrue(storeState.filesystem.filesByPath.containsKey("/Store/catalog.txt"))
        assertTrue(storeState.filesystem.filesByPath.values.any { it.name == "note-1.txt" && it.contents == "shard store note" })
        assertEquals("Canonical Store", storeState.website.title)
        assertEquals(9, storeState.website.voteCount)
        assertTrue(offlineState.ports.none { it.installedApplication?.kind?.name == "HTTP" })
        assertEquals("Offline Page", offlineState.website.title)
        assertTrue(npcState.identity.isNpc)
        assertTrue(npcState.filesystem.filesByPath["/Public/http"]?.scriptBundle?.script(ProgramScriptSlot.ENTER)?.contains("triggerWatchRemote") == true)
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
