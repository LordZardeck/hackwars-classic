package com.hackwars.rewrite.persistence

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

@Testcontainers
class JdbcImportAuditRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_import_audit_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val sink = JdbcRewriteSeedSink(connectionFactory = ::newConnection)

    @Test
    fun listsImportBatchesAndBuildsCleanRetainedReconciliationReport() {
        resetDatabase()
        retainedFixtureBatches().forEach(::writeBatch)
        val repository = JdbcImportAuditRepository(connectionFactory = ::newConnection)

        val batches = runBlocking { repository.listImportBatches() }
        val report = runBlocking { repository.buildRetainedReconciliationReport() }

        assertEquals(9, batches.size)
        assertEquals(
            listOf(
                "chat-social",
                "computer",
                "inventory",
                "player",
                "player",
                "player",
                "service-session",
                "session-ticket",
                "world",
            ),
            batches.map { it.payloadType }.sorted(),
        )
        assertEquals(
            mapOf(
                "chat-social" to 1,
                "computer" to 1,
                "inventory" to 1,
                "player" to 3,
                "service-session" to 1,
                "session-ticket" to 1,
                "world" to 1,
            ),
            report.payloadCountsByType,
        )
        assertEquals(0, report.mismatchCount)
        assertEquals(emptyList(), report.missingWebsiteStateIds)
        assertEquals(emptyList(), report.missingChatPresenceConnectionIds)
    }

    @Test
    fun retainedReconciliationReportFlagsMissingProjectionAndPresenceRows() {
        resetDatabase()
        retainedFixtureBatches().forEach(::writeBatch)
        newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("delete from rewrite_website_projection where state_id = '198.51.100.10'")
                statement.executeUpdate("delete from rewrite_chat_presence where connection_id = 'chat-1'")
            }
        }
        val repository = JdbcImportAuditRepository(connectionFactory = ::newConnection)

        val report = runBlocking { repository.buildRetainedReconciliationReport() }

        assertEquals(listOf("198.51.100.10"), report.missingWebsiteStateIds)
        assertEquals(listOf("chat-1"), report.missingChatPresenceConnectionIds)
        assertEquals(2, report.mismatchCount)
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

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
