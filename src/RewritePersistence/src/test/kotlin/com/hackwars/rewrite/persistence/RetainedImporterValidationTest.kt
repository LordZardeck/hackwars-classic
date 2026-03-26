package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.GameStateId
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Testcontainers
class RetainedImporterValidationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_retained_importer_validation_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val sink = JdbcRewriteSeedSink(connectionFactory = ::newConnection)

    @Test
    fun retainedImporterFixtureSeedsAllRetainedRepositories() {
        resetDatabase()
        retainedFixtureBatches().forEach(::writeBatch)

        val authRepository = JdbcAuthSessionRepository(connectionFactory = ::newConnection)
        val networkRepository = JdbcNetworkDirectoryRepository(connectionFactory = ::newConnection)
        val websiteRepository = JdbcWebsiteProjectionRepository(connectionFactory = ::newConnection)
        val chatRepository = JdbcChatSocialRepository(connectionFactory = ::newConnection)
        val searchRepository = JdbcSearchCatalogRepository(connectionFactory = ::newConnection)

        val ticket = runBlocking { authRepository.findSessionTicket("SESSION-LOCAL") }
        val session = runBlocking {
            authRepository.findServiceSession(PersistedServiceKind.GAME, "game-1")
        }
        val network = runBlocking { networkRepository.loadNetwork(ROOT_NETWORK_NAME) }
        val website = runBlocking { websiteRepository.findWebsiteProjection("198.51.100.10") }
        val searchDocument = runBlocking { searchRepository.loadDocuments() }.single()
        val channels = runBlocking { chatRepository.listChannels() }
        val memberships = runBlocking { chatRepository.listMemberships("global") }
        val history = runBlocking { chatRepository.loadChannelHistory("global", limit = 10) }
        val friends = runBlocking { chatRepository.listRelations("local-user", PersistedRelationKind.FRIEND) }
        val mutes = runBlocking { chatRepository.listChannelMutes("local-user", "global") }
        val presence = runBlocking { chatRepository.listActivePresence("local-user") }

        assertEquals(9, countRows("rewrite_import_batch"))

        assertNotNull(ticket)
        assertEquals("local-user", ticket.playerId)
        assertEquals("198.51.100.10", ticket.playerIp)

        assertNotNull(session)
        assertEquals("SESSION-LOCAL", session.sessionTicket)
        assertEquals("198.51.100.10", session.playerIp)

        assertNotNull(network)
        assertEquals(GameStateId("203.0.113.20"), network.storeStateId)
        assertEquals("Root Hunter", network.regularNpcs.single().displayName)
        assertEquals("Quest Guide", network.questNpcs.single().displayName)
        assertEquals("Root Miner", network.miningNpcs.single().displayName)
        assertEquals("Shard Store", network.storeNpcs.single().displayName)

        assertNotNull(website)
        assertEquals("198.51.100.10", website.canonicalAddress)
        assertEquals("Fixture Website", website.title)
        assertEquals(7, website.voteCount)
        assertEquals(3, website.votesAvailable)

        assertEquals("198.51.100.10", searchDocument.address)
        assertEquals("Fixture Website", searchDocument.title)
        assertEquals("<html>fixture body</html>", searchDocument.body)

        assertEquals(listOf("global"), channels.map { it.channelId })
        assertEquals(listOf("bob", "local-user"), memberships.map { it.playerId }.sorted())
        assertEquals(1, history.size)
        assertContentEquals("Hello Bob".encodeToByteArray(), history.single().payload)
        assertEquals(listOf("charlie"), friends.map { it.targetPlayerId })
        assertEquals(listOf("charlie"), mutes.map { it.mutedPlayerId })
        assertEquals(listOf("chat-1"), presence.map { it.connectionId })
    }

    private fun retainedFixtureBatches(): List<RewriteSeedBatch> {
        return listOf(
            RewriteSeedBatch(
                batchId = "player-local",
                source = LegacyMySqlDumpDescriptor("/legacy/local.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "player-bob",
                source = LegacyMySqlDumpDescriptor("/legacy/bob.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "bob",
                    playFabId = "PF-BOB",
                    playerIp = "198.51.100.11",
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "player-charlie",
                source = LegacyMySqlDumpDescriptor("/legacy/charlie.sql", "hackwars"),
                seedPayload = SeedPlayerAccount(
                    playerId = "charlie",
                    playFabId = "PF-CHARLIE",
                    playerIp = "198.51.100.12",
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "ticket-local",
                source = LegacyMySqlDumpDescriptor("/legacy/forum.sql", "hackwars"),
                seedPayload = PersistedSessionTicket(
                    sessionTicket = "SESSION-LOCAL",
                    playerId = "local-user",
                    playFabId = "PF-LOCAL",
                    playerIp = "198.51.100.10",
                    issuedAt = Instant.parse("2026-03-26T10:15:30Z"),
                    expiresAt = Instant.parse("2026-03-26T11:15:30Z"),
                    ticketPayload = """{"source":"fixture"}""",
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "service-local",
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
            RewriteSeedBatch(
                batchId = "computer-local",
                source = LegacyXmlDescriptor("/legacy/local.xml", "computer"),
                seedPayload = SeedComputerState(
                    computerId = "198.51.100.10",
                    playerId = "local-user",
                    ipAddress = "198.51.100.10",
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "world-retained",
                source = LegacyJsonDescriptor("/legacy/world.json", "world"),
                seedPayload = SeedWorldDirectory(
                    networks = listOf(
                        SeedWorldNetworkDefinition(
                            name = ROOT_NETWORK_NAME,
                            storeStateId = "203.0.113.20",
                            attachedNetworks = listOf(
                                SeedAttachedNetworkLink(
                                    targetNetworkName = "ProgNet",
                                    entranceMessage = "UGOPNet uplink engaged.",
                                    failureMessage = "A gateway to ProgNet is currently locked.",
                                ),
                            ),
                            npcs = listOf(
                                SeedWorldNpcEntry("203.0.113.30", "Root Hunter", "Attack NPC", NpcCategory.REGULAR),
                                SeedWorldNpcEntry("203.0.113.31", "Quest Guide", "Quest NPC", NpcCategory.QUEST),
                                SeedWorldNpcEntry("203.0.113.32", "Root Miner", "Mining NPC", NpcCategory.MINING, "Silicon"),
                                SeedWorldNpcEntry("203.0.113.20", "Shard Store", "Store NPC", NpcCategory.STORE),
                            ),
                        ),
                        SeedWorldNetworkDefinition(
                            name = "ProgNet",
                            storeStateId = "203.0.113.20",
                            attachedNetworks = listOf(
                                SeedAttachedNetworkLink(
                                    targetNetworkName = ROOT_NETWORK_NAME,
                                    entranceMessage = "ProgNet relay engaged.",
                                    failureMessage = "The uplink back to UGOPNet is unstable.",
                                ),
                            ),
                        ),
                    ),
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "inventory-local",
                source = LegacyJsonDescriptor("/legacy/local-inventory.json", "inventory"),
                seedPayload = SeedInventorySnapshot(
                    computerId = "198.51.100.10",
                    notes = listOf("fixture note"),
                    websiteTitle = "Fixture Website",
                    websiteBody = "<html>fixture body</html>",
                    votesAvailable = 3,
                    voteCount = 7,
                    pettyCash = 250.0,
                    bankMoney = 1_500.0,
                    currentNetworkName = ROOT_NETWORK_NAME,
                    allowedNetworks = listOf("ProgNet"),
                    lastNetworkSwitchAtEpochMillis = 123_456L,
                    lastLoginAtEpochMillis = 456_789L,
                    enableHttp = true,
                ),
                createdAt = Instant.EPOCH,
            ),
            RewriteSeedBatch(
                batchId = "chat-social-retained",
                source = LegacyJsonDescriptor("/legacy/chat-social.json", "chat-social"),
                seedPayload = SeedChatSocialSnapshot(
                    channels = listOf(
                        PersistedChatChannel(
                            channelId = "global",
                            displayName = "Global",
                            topic = "General chat",
                            ownerPlayerId = "local-user",
                            createdAt = Instant.parse("2026-03-26T12:00:00Z"),
                            channelPayload = """{"kind":"global"}""",
                        ),
                    ),
                    memberships = listOf(
                        PersistedChatChannelMembership(
                            channelId = "global",
                            playerId = "local-user",
                            role = PersistedChannelRole.OWNER,
                            joinedAt = Instant.parse("2026-03-26T12:00:01Z"),
                            membershipPayload = """{"grantedBy":"system"}""",
                        ),
                        PersistedChatChannelMembership(
                            channelId = "global",
                            playerId = "bob",
                            role = PersistedChannelRole.MEMBER,
                            joinedAt = Instant.parse("2026-03-26T12:00:02Z"),
                            membershipPayload = """{"grantedBy":"local-user"}""",
                        ),
                    ),
                    messages = listOf(
                        PersistedChatMessage(
                            messageId = "msg-1",
                            messageKind = PersistedChatMessageKind.CHANNEL,
                            channelId = "global",
                            senderPlayerId = "local-user",
                            eventType = "CHAT_MESSAGE",
                            payload = "Hello Bob".encodeToByteArray(),
                            createdAt = Instant.parse("2026-03-26T12:01:00Z"),
                        ),
                    ),
                    relations = listOf(
                        PersistedChatRelation(
                            playerId = "local-user",
                            targetPlayerId = "charlie",
                            relationKind = PersistedRelationKind.FRIEND,
                            createdAt = Instant.parse("2026-03-26T12:02:00Z"),
                            relationPayload = """{"source":"fixture"}""",
                        ),
                    ),
                    channelMutes = listOf(
                        PersistedChannelMute(
                            playerId = "local-user",
                            channelId = "global",
                            mutedPlayerId = "charlie",
                            createdAt = Instant.parse("2026-03-26T12:03:00Z"),
                            mutePayload = """{"reason":"spam"}""",
                        ),
                    ),
                    presence = listOf(
                        PersistedChatPresence(
                            connectionId = "chat-1",
                            playerId = "local-user",
                            onlineAt = Instant.parse("2026-03-26T12:04:00Z"),
                            lastSeenAt = Instant.parse("2026-03-26T12:04:30Z"),
                            presencePayload = """{"service":"CHAT"}""",
                        ),
                    ),
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
