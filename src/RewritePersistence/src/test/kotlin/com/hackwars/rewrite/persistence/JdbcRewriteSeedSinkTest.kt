package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.JAIL_NETWORK_NAME
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.ProgramScriptSlot
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StoredFileKind
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

        writeBatch(worldDirectoryBatch())
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
                    pettyCash = 450.0,
                    bankMoney = 125.0,
                    commodities = listOf(3.0, 1.0, 4.0, 1.0, 5.0),
                    commodityRespawn = listOf(5.0, 1.0, 4.0, 1.0, 3.0),
                    currentNetworkName = "ProgNet",
                    allowedNetworks = listOf("ProgNet"),
                    lastNetworkSwitchAtEpochMillis = 123_456L,
                    scanningExperience = 240.0,
                    firewallExperience = 80.0,
                    currentCpuLoad = 15.0,
                    cpuMax = 100.0,
                    memoryType = 0,
                    freezeImmune = true,
                    destroyWatchesImmune = true,
                    activeQuestLabelsById = mapOf("quest-1" to "Starter Quest"),
                    seedSaveFileName = "migration",
                    enableWatchBinary = true,
                    seedInstalledWatchCount = 1,
                    seedEnabledWatchCount = 0,
                    seedWatchCpuCost = 5.0,
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
                    seedInstalledWatchCount = 21,
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
                    currentNetworkName = JAIL_NETWORK_NAME,
                    lastNetworkSwitchAtEpochMillis = 999L,
                    currentCpuLoad = 20.0,
                    enableHttp = false,
                    seedInstalledWatchCount = 4,
                    seedEnabledWatchCount = 4,
                    seedWatchCpuCost = 5.0,
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

        assertEquals(13, countRows("rewrite_import_batch"))
        assertEquals(4, countRows("rewrite_player_account"))
        assertEquals(4, countRows("rewrite_computer_state"))
        assertEquals(3, countRows("rewrite_network_directory"))
        assertEquals(2, countRows("rewrite_network_link"))
        assertEquals(8, countRows("rewrite_network_npc"))

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
        assertEquals(ScriptFamily.WATCH, state.filesystem.filesByPath["/Public/watch.bin"]?.compiledBinary?.scriptFamily)
        assertTrue(state.filesystem.filesByPath["/Public/http"]?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true)
        assertTrue(state.filesystem.filesByPath["/Public/http.bin"]?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true)
        assertTrue(state.filesystem.filesByPath["/Public/http"]?.scriptBundle?.script(ProgramScriptSlot.ENTER)?.contains("logMessage") == true)
        assertEquals(0.8, state.ports.single { it.number == 80 }.installedFirewall?.combatProfile?.httpDamageModifier)
        assertEquals(1.5, state.ports.single { it.number == 80 }.installedFirewall?.combatProfile?.attackBackDamage)
        assertEquals("Seeded Local Page", state.website.title)
        assertEquals(2, state.website.votesAvailable)
        assertEquals(4, state.website.voteCount)
        assertEquals(450.0, state.economy.pettyCash)
        assertEquals(125.0, state.economy.bankMoney)
        assertEquals(listOf(3.0, 1.0, 4.0, 1.0, 5.0), state.economy.commodities)
        assertEquals(listOf(5.0, 1.0, 4.0, 1.0, 3.0), state.economy.commodityRespawn)
        assertEquals(6, state.economy.defaultBankPort)
        assertEquals("ProgNet", state.network.currentNetworkName)
        assertEquals(GameStateId("store1"), state.network.storeStateId)
        assertEquals(setOf("ProgNet"), state.network.allowedNetworks)
        assertEquals(123_456L, state.network.lastNetworkSwitchAtEpochMillis)
        assertEquals("Prog Courier", state.network.regularNpcs.single().displayName)
        assertEquals("Prog Mentor", state.network.questNpcs.single().displayName)
        assertEquals("Prog Quarry", state.network.miningNpcs.single().displayName)
        assertEquals("Shard Store", state.network.storeNpcs.single().displayName)
        assertEquals(240.0, state.stats.experienceByFamily[ScriptFamily.SCANNING])
        assertEquals(80.0, state.stats.experienceByFamily[ScriptFamily.FIREWALL])
        assertEquals(15.0, state.runtime.currentCpuLoad)
        assertTrue(state.hardware.equipmentSlots.values.any { it.freezeImmune })
        assertTrue(state.hardware.equipmentSlots.values.any { it.destroyWatchesImmune })
        assertEquals(1, state.watches.watches.size)
        assertTrue(!state.watches.watches.single().enabled)
        assertTrue(state.watches.watches.single().scriptBundle?.script(ProgramScriptSlot.FIRE)?.contains("logMessage") == true)
        assertEquals("Starter Quest", state.quests.activeQuestsById["quest-1"]?.label)
        assertEquals(StoredFileKind.SAVE_DATA, state.filesystem.filesByPath["/migration.save"]?.kind)
        assertTrue(state.filesystem.filesByPath["/migration.save"]?.saveMetadata?.valuesByKey?.isNotEmpty() == true)
        assertTrue(state.ports.any { it.number == 80 && it.installedApplication?.scriptBundle?.scriptsBySlot?.isNotEmpty() == true })
        assertTrue(state.ports.any { it.number == 80 && it.defaultPort })
        assertTrue(state.ports.any { it.number == 80 && it.installedFirewall != null })
        assertTrue(storeState.filesystem.directoriesByPath.containsKey("/Store"))
        assertTrue(storeState.filesystem.filesByPath.containsKey("/Store/catalog.txt"))
        assertTrue(storeState.filesystem.filesByPath.values.any { it.name == "note-1.txt" && it.contents == "shard store note" })
        assertEquals("Canonical Store", storeState.website.title)
        assertEquals(9, storeState.website.voteCount)
        assertEquals(ROOT_NETWORK_NAME, storeState.network.currentNetworkName)
        assertEquals(GameStateId("store1"), storeState.network.storeStateId)
        assertEquals(21, storeState.watches.watches.size)
        assertTrue(storeState.watches.watches.first().scriptBundle?.script(ProgramScriptSlot.FIRE)?.contains("logMessage") == true)
        assertTrue(offlineState.ports.none { it.installedApplication?.kind?.name == "HTTP" })
        assertEquals("Offline Page", offlineState.website.title)
        assertEquals(JAIL_NETWORK_NAME, offlineState.network.currentNetworkName)
        assertEquals(999L, offlineState.network.lastNetworkSwitchAtEpochMillis)
        assertEquals(6, offlineState.economy.defaultBankPort)
        assertEquals(4, offlineState.watches.watches.count { it.enabled })
        assertEquals(20.0, offlineState.runtime.currentCpuLoad)
        assertTrue(offlineState.watches.watches.first().scriptBundle?.script(ProgramScriptSlot.FIRE)?.contains("logMessage") == true)
        assertTrue(npcState.identity.isNpc)
        assertEquals(ROOT_NETWORK_NAME, npcState.network.currentNetworkName)
        assertTrue(npcState.filesystem.filesByPath["/Public/http"]?.scriptBundle?.script(ProgramScriptSlot.ENTER)?.contains("triggerWatchRemote") == true)
    }

    @Test
    fun importerWritesAuthPlayerAndComputerRowsThroughSeedSink() {
        resetDatabase()

        writeBatch(
            RewriteSeedBatch(
                batchId = "player-auth",
                source = LegacyMySqlDumpDescriptor("/legacy/local.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "ticket-auth",
                source = LegacyMySqlDumpDescriptor("/legacy/forum.sql", "hackwars"),
                seedPayload = PersistedSessionTicket(
                    sessionTicket = "SESSION-LOCAL",
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                    issuedAt = Instant.parse("2026-03-26T10:15:30Z"),
                    expiresAt = Instant.parse("2026-03-26T11:15:30Z"),
                    ticketPayload = """{"source":"legacy-auth"}""",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "service-auth",
                source = LegacyMySqlDumpDescriptor("/legacy/forum.sql", "hackwars"),
                seedPayload = PersistedServiceSession(
                    serviceSessionId = "svc-1",
                    serviceKind = PersistedServiceKind.GAME,
                    connectionId = "game-1",
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                    sessionTicket = "SESSION-LOCAL",
                    clientBuild = "rewrite-dev",
                    heartbeatIntervalMillis = 15_000L,
                    authenticatedAt = Instant.parse("2026-03-26T10:16:00Z"),
                    lastSeenAt = Instant.parse("2026-03-26T10:17:00Z"),
                    sessionPayload = """{"bootstrapStateId":"198.51.100.10"}""",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-auth",
                source = LegacyXmlDescriptor("/legacy/local.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "198.51.100.10",
                    playerId = "local-user",
                    ipAddress = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-auth",
                source = LegacyJsonDescriptor("/legacy/local-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "198.51.100.10",
                    notes = listOf("auth note"),
                    websiteTitle = "Auth Projection Title",
                    websiteBody = "<html>Auth Projection Body</html>",
                    pettyCash = 500.0,
                ),
                createdAt = Instant.EPOCH,
            ),
        )

        val authRepository = JdbcAuthSessionRepository(connectionFactory = ::newConnection)
        val ticket = kotlinx.coroutines.runBlocking { authRepository.findSessionTicket("SESSION-LOCAL") }
        val session = kotlinx.coroutines.runBlocking {
            authRepository.findServiceSession(PersistedServiceKind.GAME, "game-1")
        }
        val state = loadStatePayload("198.51.100.10")

        assertEquals(5, countRows("rewrite_import_batch"))
        assertEquals(1, countRows("rewrite_player_account"))
        assertEquals(1, countRows("rewrite_session_ticket"))
        assertEquals(1, countRows("rewrite_service_session"))
        assertEquals(1, countRows("rewrite_computer_state"))

        assertNotNull(ticket)
        assertEquals("local-user", ticket.playerId)
        assertEquals("198.51.100.10", ticket.playerIp)
        assertNotNull(session)
        assertEquals("SESSION-LOCAL", session.sessionTicket)
        assertEquals("198.51.100.10", session.playerIp)
        assertEquals("Auth Projection Title", state.website.title)
        assertEquals(500.0, state.economy.pettyCash)
    }

    @Test
    fun importerKeepsDistinctComputerIdWhenItDiffersFromIpAddress() {
        resetDatabase()

        writeBatch(
            RewriteSeedBatch(
                batchId = "player-distinct",
                source = LegacyMySqlDumpDescriptor("/legacy/local.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "computer-distinct",
                source = LegacyXmlDescriptor("/legacy/local.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "computer-local",
                    playerId = "local-user",
                    ipAddress = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
        )
        writeBatch(
            RewriteSeedBatch(
                batchId = "inventory-distinct",
                source = LegacyJsonDescriptor("/legacy/local-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "computer-local",
                    notes = listOf("distinct id note"),
                    websiteTitle = "Distinct Computer",
                    websiteBody = "<html>Distinct</html>",
                ),
                createdAt = Instant.EPOCH,
            ),
        )

        val state = loadStatePayload("computer-local")

        assertEquals(1, countRows("rewrite_computer_state"))
        assertEquals("198.51.100.10", loadComputerIpAddress("computer-local"))
        assertTrue(!computerStateExists("198.51.100.10"))
        assertEquals(GameStateId("computer-local"), state.id)
        assertEquals("198.51.100.10", state.identity.playerIp)
        assertEquals("Distinct Computer", state.website.title)
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

    private fun loadComputerIpAddress(computerId: String): String = newConnection().use { connection ->
        connection.prepareStatement(
            """
            select ip_address
            from rewrite_computer_state
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, computerId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }
    }

    private fun computerStateExists(computerId: String): Boolean = newConnection().use { connection ->
        connection.prepareStatement(
            """
            select count(*)
            from rewrite_computer_state
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, computerId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getInt(1) > 0
            }
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
