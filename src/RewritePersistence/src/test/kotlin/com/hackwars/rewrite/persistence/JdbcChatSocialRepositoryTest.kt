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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Testcontainers
class JdbcChatSocialRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_chat_social_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    @Test
    fun upsertsChannelsMembershipsAndChannelHistory() {
        resetDatabase()
        seedPlayerAccount("alice", "PF-ALICE", "192.0.2.10")
        seedPlayerAccount("bob", "PF-BOB", "192.0.2.11")
        val repository = JdbcChatSocialRepository(connectionFactory = ::newConnection)
        val channel = PersistedChatChannel(
            channelId = "global",
            displayName = "Global",
            topic = "General chat",
            ownerPlayerId = "alice",
            privateChannel = false,
            createdAt = Instant.parse("2026-03-26T12:00:00Z"),
            channelPayload = """{"kind":"global"}""",
        )
        val ownerMembership = PersistedChatChannelMembership(
            channelId = "global",
            playerId = "alice",
            role = PersistedChannelRole.OWNER,
            joinedAt = Instant.parse("2026-03-26T12:00:01Z"),
            membershipPayload = """{"grantedBy":"system"}""",
        )
        val memberMembership = PersistedChatChannelMembership(
            channelId = "global",
            playerId = "bob",
            role = PersistedChannelRole.MEMBER,
            joinedAt = Instant.parse("2026-03-26T12:00:02Z"),
            membershipPayload = """{"grantedBy":"alice"}""",
        )
        val history = listOf(
            PersistedChatMessage(
                messageId = "msg-1",
                messageKind = PersistedChatMessageKind.CHANNEL,
                channelId = "global",
                senderPlayerId = "alice",
                eventType = "CHAT_MESSAGE",
                payload = "Hello Bob".encodeToByteArray(),
                createdAt = Instant.parse("2026-03-26T12:01:00Z"),
            ),
            PersistedChatMessage(
                messageId = "msg-2",
                messageKind = PersistedChatMessageKind.CHANNEL,
                channelId = "global",
                senderPlayerId = "bob",
                eventType = "CHAT_MESSAGE",
                payload = "Hello Alice".encodeToByteArray(),
                createdAt = Instant.parse("2026-03-26T12:01:05Z"),
            ),
        )

        runBlocking {
            repository.upsertChannel(channel)
            repository.upsertMembership(ownerMembership)
            repository.upsertMembership(memberMembership)
            history.forEach { message ->
                repository.appendMessage(message)
            }
        }

        val loadedChannel = runBlocking { repository.listChannels() }
        val loadedMemberships = runBlocking { repository.listMemberships("global") }
        val loadedHistory = runBlocking { repository.loadChannelHistory("global", limit = 10) }

        assertEquals(listOf(normalize(channel)), loadedChannel.map(::normalize))
        assertEquals(
            listOf(normalize(ownerMembership), normalize(memberMembership)),
            loadedMemberships.map(::normalize),
        )
        assertEquals(2, loadedHistory.size)
        loadedHistory.zip(history).forEach { (loaded, expected) ->
            assertEquals(expected.messageId, loaded.messageId)
            assertEquals(expected.messageKind, loaded.messageKind)
            assertEquals(expected.eventType, loaded.eventType)
            assertEquals(expected.senderPlayerId, loaded.senderPlayerId)
            assertEquals(expected.createdAt, loaded.createdAt)
            assertEquals(expected.channelId, loaded.channelId)
            assertEquals(expected.recipientPlayerId, loaded.recipientPlayerId)
            assertContentEquals(expected.payload, loaded.payload)
        }
    }

    @Test
    fun upsertsRelationsMutesAndPresence() {
        resetDatabase()
        seedPlayerAccount("alice", "PF-ALICE", "192.0.2.10")
        seedPlayerAccount("bob", "PF-BOB", "192.0.2.11")
        seedPlayerAccount("charlie", "PF-CHARLIE", "192.0.2.12")
        val repository = JdbcChatSocialRepository(connectionFactory = ::newConnection)
        runBlocking {
            repository.upsertChannel(
                PersistedChatChannel(
                    channelId = "global",
                    displayName = "Global",
                    ownerPlayerId = "alice",
                    createdAt = Instant.parse("2026-03-26T12:00:00Z"),
                ),
            )
            repository.upsertRelation(
                PersistedChatRelation(
                    playerId = "alice",
                    targetPlayerId = "bob",
                    relationKind = PersistedRelationKind.FRIEND,
                    createdAt = Instant.parse("2026-03-26T12:05:00Z"),
                    relationPayload = """{"source":"import"}""",
                ),
            )
            repository.upsertRelation(
                PersistedChatRelation(
                    playerId = "alice",
                    targetPlayerId = "charlie",
                    relationKind = PersistedRelationKind.IGNORED,
                    createdAt = Instant.parse("2026-03-26T12:06:00Z"),
                    relationPayload = """{"source":"import"}""",
                ),
            )
            repository.upsertChannelMute(
                PersistedChannelMute(
                    playerId = "alice",
                    channelId = "global",
                    mutedPlayerId = "charlie",
                    createdAt = Instant.parse("2026-03-26T12:07:00Z"),
                    mutePayload = """{"reason":"spam"}""",
                ),
            )
            repository.upsertPresence(
                PersistedChatPresence(
                    connectionId = "chat-1",
                    playerId = "alice",
                    onlineAt = Instant.parse("2026-03-26T12:08:00Z"),
                    lastSeenAt = Instant.parse("2026-03-26T12:08:00Z"),
                    presencePayload = """{"service":"CHAT"}""",
                ),
            )
        }

        val friends = runBlocking { repository.listRelations("alice", PersistedRelationKind.FRIEND) }
        val ignored = runBlocking { repository.listRelations("alice", PersistedRelationKind.IGNORED) }
        val mutes = runBlocking { repository.listChannelMutes("alice", "global") }
        val activePresence = runBlocking { repository.listActivePresence("alice") }

        assertEquals(listOf("bob"), friends.map { it.targetPlayerId })
        assertEquals(listOf("charlie"), ignored.map { it.targetPlayerId })
        assertEquals(listOf("charlie"), mutes.map { it.mutedPlayerId })
        assertEquals(listOf("chat-1"), activePresence.map { it.connectionId })

        runBlocking {
            repository.closePresence(
                connectionId = "chat-1",
                offlineAt = Instant.parse("2026-03-26T12:09:00Z"),
            )
        }
        assertEquals(emptyList(), runBlocking { repository.listActivePresence("alice") })
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

    private fun canonicalJson(raw: String): String = Json.parseToJsonElement(raw).toString()

    private fun normalize(channel: PersistedChatChannel): PersistedChatChannel {
        return channel.copy(channelPayload = canonicalJson(channel.channelPayload))
    }

    private fun normalize(membership: PersistedChatChannelMembership): PersistedChatChannelMembership {
        return membership.copy(membershipPayload = canonicalJson(membership.membershipPayload))
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
