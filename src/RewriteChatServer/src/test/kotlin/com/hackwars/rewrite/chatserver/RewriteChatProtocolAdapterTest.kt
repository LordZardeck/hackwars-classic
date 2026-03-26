package com.hackwars.rewrite.chatserver

import com.hackwars.rewrite.persistence.AuthSessionRepository
import com.hackwars.rewrite.persistence.ChatSocialRepository
import com.hackwars.rewrite.persistence.PersistedChannelRole
import com.hackwars.rewrite.persistence.PersistedChatChannel
import com.hackwars.rewrite.persistence.PersistedChatChannelMembership
import com.hackwars.rewrite.persistence.PersistedChatMessage
import com.hackwars.rewrite.persistence.PersistedChatPresence
import com.hackwars.rewrite.persistence.PersistedChatRelation
import com.hackwars.rewrite.persistence.PersistedChannelMute
import com.hackwars.rewrite.persistence.PersistedRelationKind
import com.hackwars.rewrite.persistence.PersistedServiceKind
import com.hackwars.rewrite.persistence.PersistedServiceSession
import com.hackwars.rewrite.persistence.PersistedSessionTicket
import com.hackwars.rewrite.protocol.ChatParityEventType
import com.hackwars.rewrite.protocol.ChatRelationListEventPayload
import com.hackwars.rewrite.protocol.ChatSubChannelsEventPayload
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteChatJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteChatProtocolAdapterTest {
    @Test
    fun authBootstrapPersistsChatSessionPresenceAndEmitsRetainedSync() = runTest {
        val fixture = createFixture()

        val connection = fixture.authenticatedConnection()
        val subChannelsFrame = connection.awaitFrame()
        val relationListFrame = connection.awaitFrame()

        val bootstrapChannels = RewriteChatJson.decode(
            serializer = ChatSubChannelsEventPayload.serializer(),
            bytes = subChannelsFrame.chat_event!!.payload.toByteArray(),
        )
        val relationList = RewriteChatJson.decode(
            serializer = ChatRelationListEventPayload.serializer(),
            bytes = relationListFrame.chat_event!!.payload.toByteArray(),
        )

        assertEquals(
            ChatParityEventType.SUB_CHANNELS.wireName,
            subChannelsFrame.chat_event?.event_type,
        )
        assertEquals(
            ChatParityEventType.RELATION_LIST.wireName,
            relationListFrame.chat_event?.event_type,
        )
        assertEquals("pf-localuser", bootstrapChannels.receiverPlayerId)
        assertEquals(listOf("General-0", "Trade-0"), bootstrapChannels.channels.map { it.channelName })
        assertEquals(listOf("pf-localuser", "alice"), bootstrapChannels.channels.first().users)
        assertEquals(setOf("pf-localuser"), bootstrapChannels.channels.first().adminUsers)

        assertEquals("pf-localuser", relationList.receiverPlayerId)
        assertEquals(listOf("alice"), relationList.relations.map { it.targetPlayerId })
        assertTrue(relationList.relations.single().friend)
        assertTrue(relationList.relations.single().online)
        assertEquals("raid partner", relationList.relations.single().comment)

        val activeSession = fixture.authRepository.findServiceSession(PersistedServiceKind.CHAT, "chat-1")
        assertNotNull(activeSession)
        assertEquals("pf-localuser", activeSession.playerId)
        assertEquals("PF-LOCALUSER", activeSession.playFabId)

        val presence = fixture.chatRepository.listActivePresence("pf-localuser")
        assertEquals(listOf("chat-1"), presence.map { it.connectionId })
    }

    @Test
    fun disconnectClosesChatServiceSessionAndPresence() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()
        connection.close()

        val closedSession = fixture.authRepository.findServiceSession(PersistedServiceKind.CHAT, "chat-1")
        assertNotNull(closedSession)
        assertNotNull(closedSession.closedAt)
        assertEquals(emptyList(), fixture.chatRepository.listActivePresence("pf-localuser"))
    }

    private fun TestScope.createFixture(): Fixture {
        val authRepository = InMemoryAuthSessionRepository().apply {
            upsertSessionTicketDirect(
                PersistedSessionTicket(
                    sessionTicket = "SESSION-LOCALUSER",
                    playerId = "pf-localuser",
                    playFabId = "PF-LOCALUSER",
                    playerIp = "192.0.2.10",
                    issuedAt = Instant.parse("2026-03-26T10:00:00Z"),
                ),
            )
        }
        val chatRepository = InMemoryChatSocialRepository(
            channels = mutableListOf(
                PersistedChatChannel(
                    channelId = "General-0",
                    displayName = "General-0",
                    ownerPlayerId = "pf-localuser",
                    createdAt = Instant.parse("2026-03-26T10:00:00Z"),
                    channelPayload = """{"kind":"auto","adminCanKick":false}""",
                ),
                PersistedChatChannel(
                    channelId = "Trade-0",
                    displayName = "Trade-0",
                    ownerPlayerId = "pf-localuser",
                    createdAt = Instant.parse("2026-03-26T10:01:00Z"),
                    channelPayload = """{"kind":"auto","adminCanKick":false}""",
                ),
            ),
            memberships = mutableListOf(
                PersistedChatChannelMembership(
                    channelId = "General-0",
                    playerId = "pf-localuser",
                    role = PersistedChannelRole.OWNER,
                    joinedAt = Instant.parse("2026-03-26T10:00:00Z"),
                ),
                PersistedChatChannelMembership(
                    channelId = "General-0",
                    playerId = "alice",
                    role = PersistedChannelRole.MEMBER,
                    joinedAt = Instant.parse("2026-03-26T10:02:00Z"),
                ),
                PersistedChatChannelMembership(
                    channelId = "Trade-0",
                    playerId = "pf-localuser",
                    role = PersistedChannelRole.MEMBER,
                    joinedAt = Instant.parse("2026-03-26T10:03:00Z"),
                ),
            ),
            relations = mutableListOf(
                PersistedChatRelation(
                    playerId = "pf-localuser",
                    targetPlayerId = "alice",
                    relationKind = PersistedRelationKind.FRIEND,
                    createdAt = Instant.parse("2026-03-26T10:04:00Z"),
                    relationPayload = """{"comment":"raid partner"}""",
                ),
            ),
            presence = mutableListOf(
                PersistedChatPresence(
                    connectionId = "chat-alice",
                    playerId = "alice",
                    onlineAt = Instant.parse("2026-03-26T10:05:00Z"),
                    lastSeenAt = Instant.parse("2026-03-26T10:05:00Z"),
                ),
            ),
        )
        val harness = InMemoryRewriteServiceHarness(
            adapter = RewriteChatProtocolAdapter(
                authSessionRepository = authRepository,
                chatSocialRepository = chatRepository,
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ).serviceAdapter(),
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount(
                            playFabId = "PF-LOCALUSER",
                            playerIp = "192.0.2.10",
                            sessionTicket = "SESSION-LOCALUSER",
                        ),
                    ),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        return Fixture(
            harness = harness,
            authRepository = authRepository,
            chatRepository = chatRepository,
        )
    }

    private suspend fun Fixture.authenticatedConnection(): InMemoryClientConnection {
        val connection = harness.connect(connectionPrefix = "chat")
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.CHAT,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-chat-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = "192.0.2.10",
            ),
        )
        val accepted = connection.awaitFrame()
        assertNotNull(accepted.auth_response?.accepted)
        assertEquals("chat-1", accepted.auth_response?.accepted?.connection_id)
        return connection
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val authRepository: InMemoryAuthSessionRepository,
        val chatRepository: InMemoryChatSocialRepository,
    )
}

private class InMemoryAuthSessionRepository : AuthSessionRepository {
    private val tickets = linkedMapOf<String, PersistedSessionTicket>()
    private val sessions = linkedMapOf<Pair<PersistedServiceKind, String>, PersistedServiceSession>()

    fun upsertSessionTicketDirect(ticket: PersistedSessionTicket) {
        tickets[ticket.sessionTicket] = ticket
    }

    override suspend fun upsertSessionTicket(ticket: PersistedSessionTicket) {
        tickets[ticket.sessionTicket] = ticket
    }

    override suspend fun findSessionTicket(sessionTicket: String): PersistedSessionTicket? = tickets[sessionTicket]

    override suspend fun upsertServiceSession(session: PersistedServiceSession) {
        sessions[session.serviceKind to session.connectionId] = session
    }

    override suspend fun findServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
    ): PersistedServiceSession? = sessions[serviceKind to connectionId]

    override suspend fun listActiveServiceSessions(playerId: String): List<PersistedServiceSession> {
        return sessions.values.filter { it.playerId == playerId && it.closedAt == null }
    }

    override suspend fun touchServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        lastSeenAt: Instant,
    ) {
        val key = serviceKind to connectionId
        sessions[key] = sessions[key]?.copy(lastSeenAt = lastSeenAt) ?: return
    }

    override suspend fun closeServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        closedAt: Instant,
    ) {
        val key = serviceKind to connectionId
        val current = sessions[key] ?: return
        sessions[key] = current.copy(
            closedAt = closedAt,
            lastSeenAt = maxOf(current.lastSeenAt, closedAt),
        )
    }
}

private class InMemoryChatSocialRepository(
    private val channels: MutableList<PersistedChatChannel> = mutableListOf(),
    private val memberships: MutableList<PersistedChatChannelMembership> = mutableListOf(),
    private val relations: MutableList<PersistedChatRelation> = mutableListOf(),
    private val presence: MutableList<PersistedChatPresence> = mutableListOf(),
) : ChatSocialRepository {
    override suspend fun upsertChannel(channel: PersistedChatChannel) {
        channels.removeAll { it.channelId == channel.channelId }
        channels += channel
    }

    override suspend fun listChannels(): List<PersistedChatChannel> = channels.sortedBy { it.createdAt }

    override suspend fun upsertMembership(membership: PersistedChatChannelMembership) {
        memberships.removeAll { it.channelId == membership.channelId && it.playerId == membership.playerId }
        memberships += membership
    }

    override suspend fun listMemberships(channelId: String): List<PersistedChatChannelMembership> {
        return memberships.filter { it.channelId == channelId }.sortedBy { it.joinedAt }
    }

    override suspend fun appendMessage(message: PersistedChatMessage) = Unit

    override suspend fun loadChannelHistory(
        channelId: String,
        limit: Int,
    ): List<PersistedChatMessage> = emptyList()

    override suspend fun upsertRelation(relation: PersistedChatRelation) {
        relations.removeAll {
            it.playerId == relation.playerId &&
                it.targetPlayerId == relation.targetPlayerId &&
                it.relationKind == relation.relationKind
        }
        relations += relation
    }

    override suspend fun listRelations(
        playerId: String,
        relationKind: PersistedRelationKind,
    ): List<PersistedChatRelation> {
        return relations.filter { it.playerId == playerId && it.relationKind == relationKind }
    }

    override suspend fun upsertChannelMute(mute: PersistedChannelMute) = Unit

    override suspend fun listChannelMutes(
        playerId: String,
        channelId: String,
    ): List<PersistedChannelMute> = emptyList()

    override suspend fun upsertPresence(presence: PersistedChatPresence) {
        this.presence.removeAll { it.connectionId == presence.connectionId }
        this.presence += presence
    }

    override suspend fun listActivePresence(playerId: String?): List<PersistedChatPresence> {
        return presence.filter { it.offlineAt == null && (playerId == null || it.playerId == playerId) }
    }

    override suspend fun closePresence(
        connectionId: String,
        offlineAt: Instant,
    ) {
        presence.replaceAll { current ->
            if (current.connectionId == connectionId) {
                current.copy(
                    offlineAt = offlineAt,
                    lastSeenAt = maxOf(current.lastSeenAt, offlineAt),
                )
            } else {
                current
            }
        }
    }
}
