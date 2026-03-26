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
import com.hackwars.rewrite.protocol.ChatAddAdminPayload
import com.hackwars.rewrite.protocol.ChatChannelAddEventPayload
import com.hackwars.rewrite.protocol.ChatChannelCreatePayload
import com.hackwars.rewrite.protocol.ChatChannelJoinPayload
import com.hackwars.rewrite.protocol.ChatChannelKickPayload
import com.hackwars.rewrite.protocol.ChatChannelLeavePayload
import com.hackwars.rewrite.protocol.ChatChannelTextEventPayload
import com.hackwars.rewrite.protocol.ChatChannelTextPayload
import com.hackwars.rewrite.protocol.ChatErrorEventPayload
import com.hackwars.rewrite.protocol.ChatMutePayload
import com.hackwars.rewrite.protocol.ChatParityEventType
import com.hackwars.rewrite.protocol.ChatRelationListEventPayload
import com.hackwars.rewrite.protocol.ChatSubChannelsPayload
import com.hackwars.rewrite.protocol.ChatSubChannelsEventPayload
import com.hackwars.rewrite.protocol.ChatWhisperEventPayload
import com.hackwars.rewrite.protocol.ChatWhisperPayload
import com.hackwars.rewrite.protocol.DisconnectReason
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteChatJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.FrameEnvelope
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
        assertEquals(listOf("General-0", "Trade-0", "Help-0"), bootstrapChannels.channels.map { it.channelName })
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
        assertEquals(listOf("pf-localuser"), fixture.chatRepository.listMemberships("Help-0").map { it.playerId })
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

    @Test
    fun pingTouchesChatServiceSessionAndPresence() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        testScheduler.advanceTimeBy(5_000)
        connection.send(
            RewriteFrames.ping(
                connectionId = connection.connectionId,
                sentAtEpochMillis = 1_000,
                acknowledgedAtEpochMillis = 0,
            ),
        )

        val pingResponse = connection.awaitFrame()
        val touchedSession = fixture.authRepository.findServiceSession(PersistedServiceKind.CHAT, "chat-1")
        val presence = fixture.chatRepository.listActivePresence("pf-localuser").single()

        assertEquals("chat-1", pingResponse.ping?.connection_id)
        assertEquals(1_000, pingResponse.ping?.sent_at_epoch_millis)
        assertEquals(5_000, pingResponse.ping?.acknowledged_at_epoch_millis)
        assertNotNull(touchedSession)
        assertEquals(Instant.ofEpochMilli(5_000), touchedSession.lastSeenAt)
        assertEquals(Instant.ofEpochMilli(5_000), presence.lastSeenAt)
    }

    @Test
    fun idleTimeoutClosesChatServiceSessionAndPresence() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        testScheduler.advanceTimeBy(46_000)
        fixture.harness.sweepTimeouts()

        val closedSession = fixture.authRepository.findServiceSession(PersistedServiceKind.CHAT, "chat-1")

        assertEquals(DisconnectReason.IDLE_TIMEOUT, connection.disconnectReason())
        assertNotNull(closedSession)
        assertNotNull(closedSession.closedAt)
        assertEquals(emptyList(), fixture.chatRepository.listActivePresence("pf-localuser"))
    }

    @Test
    fun boundTransportPushesFramesToActiveRecipientConnections() = runTest {
        val fixture = createFixture()
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        fixture.adapter.pushToPlayerIds(
            playerIds = setOf("alice"),
            frames = listOf(
                RewriteFrames.chatEvent(
                    eventId = "fanout-1",
                    eventType = ChatParityEventType.ERROR,
                    payload = RewriteChatJson.encode(
                        serializer = ChatErrorEventPayload.serializer(),
                        value = ChatErrorEventPayload(
                            receiverPlayerId = "alice",
                            message = "fanout",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("fanout", RewriteChatJson.decode(
            serializer = ChatErrorEventPayload.serializer(),
            bytes = aliceConnection.awaitFrame().chat_event!!.payload.toByteArray(),
        ).message)
        assertEquals(emptyList(), localConnection.drainFrames())
    }

    @Test
    fun subChannelsCommandReturnsActorLocalRefresh() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "sub_channels",
            payload = ChatSubChannelsPayload(senderPlayerId = "pf-localuser"),
            serializer = ChatSubChannelsPayload.serializer(),
        )

        val response = frames.commandResponse()
        val refresh = frames.subChannelsEvent()

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, response.command_response?.status)
        assertEquals(listOf("General-0", "Trade-0", "Help-0"), refresh.channels.map { it.channelName })
    }

    @Test
    fun createChannelCreatesOwnerMembershipAndRefreshesSubscribedChannels() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "channel_create",
            payload = ChatChannelCreatePayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                password = "secret",
            ),
            serializer = ChatChannelCreatePayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(
            listOf("General-0", "Trade-0", "Help-0", "Ops"),
            frames.subChannelsEvent().channels.map { it.channelName },
        )
        val createdChannel = fixture.chatRepository.listChannels().first { it.channelId == "Ops" }
        assertTrue(createdChannel.privateChannel)
        assertEquals("pf-localuser", createdChannel.ownerPlayerId)
        assertEquals(
            listOf("pf-localuser"),
            fixture.chatRepository.listMemberships("Ops").map { it.playerId },
        )
        assertEquals(
            PersistedChannelRole.OWNER,
            fixture.chatRepository.listMemberships("Ops").single().role,
        )
    }

    @Test
    fun createExistingChannelFallsBackToJoinAndEmitsLegacyStyleError() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "alice",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
                channelPayload = """{"kind":"user","removeWhenEmpty":true}""",
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "channel_create",
            payload = ChatChannelCreatePayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                password = "",
            ),
            serializer = ChatChannelCreatePayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        val error = frames.errorEvent()
        assertTrue(error.message.contains("already exists"))
        assertEquals(listOf("General-0", "Trade-0", "Help-0", "Ops"), frames.subChannelsEvent().channels.map { it.channelName })
    }

    @Test
    fun leaveChannelTransfersOwnershipToNextMember() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
                channelPayload = """{"kind":"user","removeWhenEmpty":true}""",
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "channel_leave",
            payload = ChatChannelLeavePayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
            ),
            serializer = ChatChannelLeavePayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(listOf("General-0", "Trade-0", "Help-0"), frames.subChannelsEvent().channels.map { it.channelName })
        assertEquals(listOf("alice"), fixture.chatRepository.listMemberships("Ops").map { it.playerId })
        assertEquals(PersistedChannelRole.OWNER, fixture.chatRepository.listMemberships("Ops").single().role)
        assertEquals("alice", fixture.chatRepository.listChannels().first { it.channelId == "Ops" }.ownerPlayerId)
    }

    @Test
    fun kickChannelMemberRemovesTargetMembership() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
                channelPayload = """{"kind":"user","adminCanKick":true,"removeWhenEmpty":true}""",
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "channel_kick",
            payload = ChatChannelKickPayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                targetPlayerId = "alice",
            ),
            serializer = ChatChannelKickPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(listOf("General-0", "Trade-0", "Help-0", "Ops"), frames.subChannelsEvent().channels.map { it.channelName })
        assertEquals(listOf("pf-localuser"), fixture.chatRepository.listMemberships("Ops").map { it.playerId })
    }

    @Test
    fun addAdminPromotesChannelMemberToModerator() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
                channelPayload = """{"kind":"user","adminCanKick":true,"removeWhenEmpty":true}""",
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "add_admin",
            payload = ChatAddAdminPayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                receiverPlayerId = "alice",
            ),
            serializer = ChatAddAdminPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(PersistedChannelRole.MODERATOR, fixture.chatRepository.listMemberships("Ops").first { it.playerId == "alice" }.role)
        assertEquals(
            setOf("pf-localuser", "alice"),
            frames.subChannelsEvent().channels.first { it.channelName == "Ops" }.adminUsers,
        )
    }

    @Test
    fun mutePersistsChannelScopedMute() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
                channelPayload = """{"kind":"user","adminCanKick":true,"removeWhenEmpty":true}""",
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        val connection = fixture.authenticatedConnection()
        connection.awaitFrame()
        connection.awaitFrame()

        val frames = connection.sendChatCommand(
            commandName = "mute",
            payload = ChatMutePayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                receiverPlayerId = "alice",
            ),
            serializer = ChatMutePayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(
            listOf("alice"),
            fixture.chatRepository.listChannelMutes("pf-localuser", "Ops").map { it.mutedPlayerId },
        )
        assertEquals(1, frames.size)
    }

    @Test
    fun channelTextFansOutToActiveSubscribersAndPersistsHistory() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        val frames = localConnection.sendChatCommand(
            commandName = "channel_text",
            payload = ChatChannelTextPayload(
                senderPlayerId = "pf-localuser",
                message = "hello ops",
                channelName = "Ops",
            ),
            serializer = ChatChannelTextPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals("hello ops", frames.channelTextEvent().message)
        assertEquals("hello ops", aliceConnection.channelTextEvent().message)
        assertEquals(listOf(ChatParityEventType.CHANNEL_TEXT.wireName), fixture.chatRepository.loadChannelHistory("Ops", limit = 10).map { it.eventType })
    }

    @Test
    fun channelTextSkipsRecipientsWhoMutedSender() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "pf-localuser",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.MEMBER,
                joinedAt = Instant.parse("2026-03-26T10:06:02Z"),
            ),
        )
        fixture.chatRepository.upsertChannelMute(
            PersistedChannelMute(
                playerId = "alice",
                channelId = "Ops",
                mutedPlayerId = "pf-localuser",
                createdAt = Instant.parse("2026-03-26T10:06:03Z"),
                mutePayload = """{"source":"test"}""",
            ),
        )
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        val frames = localConnection.sendChatCommand(
            commandName = "channel_text",
            payload = ChatChannelTextPayload(
                senderPlayerId = "pf-localuser",
                message = "hello ops",
                channelName = "Ops",
            ),
            serializer = ChatChannelTextPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals("hello ops", frames.channelTextEvent().message)
        assertTrue(aliceConnection.drainFrames().isEmpty())
        assertEquals(listOf(ChatParityEventType.CHANNEL_TEXT.wireName), fixture.chatRepository.loadChannelHistory("Ops", limit = 10).map { it.eventType })
    }

    @Test
    fun channelJoinPushesChannelAddToActiveSubscribers() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertChannel(
            PersistedChatChannel(
                channelId = "Ops",
                displayName = "Ops",
                ownerPlayerId = "alice",
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
            ),
        )
        fixture.chatRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = "Ops",
                playerId = "alice",
                role = PersistedChannelRole.OWNER,
                joinedAt = Instant.parse("2026-03-26T10:06:01Z"),
            ),
        )
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        val frames = localConnection.sendChatCommand(
            commandName = "channel_join",
            payload = ChatChannelJoinPayload(
                senderPlayerId = "pf-localuser",
                channelName = "Ops",
                password = "",
            ),
            serializer = ChatChannelJoinPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals(listOf("General-0", "Trade-0", "Help-0", "Ops"), frames.subChannelsEvent().channels.map { it.channelName })
        assertEquals("pf-localuser", aliceConnection.channelAddEvent().userToAdd)
    }

    @Test
    fun whisperEchoesToSenderAndReceiverAndPersistsHistory() = runTest {
        val fixture = createFixture()
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        val frames = localConnection.sendChatCommand(
            commandName = "whisper",
            payload = ChatWhisperPayload(
                senderPlayerId = "pf-localuser",
                receiverPlayerId = "alice",
                message = "psst",
            ),
            serializer = ChatWhisperPayload.serializer(),
        )

        val senderEcho = frames.whisperEvent()
        val receiverWhisper = aliceConnection.whisperEvent()
        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK, frames.commandResponse().command_response?.status)
        assertEquals("psst", senderEcho.message)
        assertEquals("alice", senderEcho.receiverPlayerId)
        assertEquals("psst", receiverWhisper.message)
        assertEquals("alice", receiverWhisper.receiverPlayerId)
        assertEquals(listOf("whisper"), fixture.chatRepository.loadWhispers().map { it.eventType })
    }

    @Test
    fun whisperRejectsTargetsWhoIgnoreSender() = runTest {
        val fixture = createFixture()
        fixture.chatRepository.upsertRelation(
            PersistedChatRelation(
                playerId = "alice",
                targetPlayerId = "pf-localuser",
                relationKind = PersistedRelationKind.IGNORED,
                createdAt = Instant.parse("2026-03-26T10:06:00Z"),
            ),
        )
        val localConnection = fixture.authenticatedConnection()
        localConnection.awaitFrame()
        localConnection.awaitFrame()
        val aliceConnection = fixture.authenticatedConnection(
            sessionTicket = "SESSION-ALICE",
            playFabIdHint = "PF-ALICE",
            requestedIp = "192.0.2.11",
        )
        aliceConnection.awaitFrame()
        aliceConnection.awaitFrame()

        val frames = localConnection.sendChatCommand(
            commandName = "whisper",
            payload = ChatWhisperPayload(
                senderPlayerId = "pf-localuser",
                receiverPlayerId = "alice",
                message = "psst",
            ),
            serializer = ChatWhisperPayload.serializer(),
        )

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR, frames.commandResponse().command_response?.status)
        assertEquals("Retained chat whisper target `alice` has you ignored.", frames.errorEvent().message)
        assertTrue(aliceConnection.drainFrames().isEmpty())
        assertTrue(fixture.chatRepository.loadWhispers().isEmpty())
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
            upsertSessionTicketDirect(
                PersistedSessionTicket(
                    sessionTicket = "SESSION-ALICE",
                    playerId = "alice",
                    playFabId = "PF-ALICE",
                    playerIp = "192.0.2.11",
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
        val adapter = RewriteChatProtocolAdapter(
            authSessionRepository = authRepository,
            chatSocialRepository = chatRepository,
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        val harness = InMemoryRewriteServiceHarness(
            adapter = adapter.serviceAdapter(),
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount(
                            playFabId = "PF-LOCALUSER",
                            playerIp = "192.0.2.10",
                            sessionTicket = "SESSION-LOCALUSER",
                        ),
                        FakePlayerAccount(
                            playFabId = "PF-ALICE",
                            playerIp = "192.0.2.11",
                            sessionTicket = "SESSION-ALICE",
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
            adapter = adapter,
            authRepository = authRepository,
            chatRepository = chatRepository,
        )
    }

    private suspend fun Fixture.authenticatedConnection(
        sessionTicket: String = "SESSION-LOCALUSER",
        playFabIdHint: String = "PF-LOCALUSER",
        requestedIp: String = "192.0.2.10",
    ): InMemoryClientConnection {
        val connection = harness.connect(connectionPrefix = "chat")
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.CHAT,
                sessionTicket = sessionTicket,
                clientBuild = "rewrite-chat-it",
                playFabIdHint = playFabIdHint,
                requestedIp = requestedIp,
            ),
        )
        val accepted = connection.awaitFrame()
        assertNotNull(accepted.auth_response?.accepted)
        return connection
    }

    private suspend fun <T> InMemoryClientConnection.sendChatCommand(
        commandName: String,
        payload: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        commandId: String = "$commandName-1",
    ): List<FrameEnvelope> {
        return sendChatCommand(
            commandName = commandName,
            payload = RewriteChatJson.encode(serializer, payload),
            commandId = commandId,
        )
    }

    private suspend fun InMemoryClientConnection.sendChatCommand(
        commandName: String,
        payload: ByteArray,
        commandId: String = "$commandName-1",
    ): List<FrameEnvelope> {
        send(
            RewriteFrames.command(
                commandId = commandId,
                commandName = commandName,
                payload = payload,
                expectsResponse = true,
            ),
        )
        return drainFrames()
    }

    private fun List<FrameEnvelope>.commandResponse(): FrameEnvelope {
        return first { it.command_response != null }
    }

    private fun List<FrameEnvelope>.subChannelsEvent(): ChatSubChannelsEventPayload {
        val frame = first { it.chat_event?.event_type == ChatParityEventType.SUB_CHANNELS.wireName }
        return RewriteChatJson.decode(
            serializer = ChatSubChannelsEventPayload.serializer(),
            bytes = frame.chat_event!!.payload.toByteArray(),
        )
    }

    private fun List<FrameEnvelope>.errorEvent(): ChatErrorEventPayload {
        val frame = first { it.chat_event?.event_type == ChatParityEventType.ERROR.wireName }
        return RewriteChatJson.decode(
            serializer = ChatErrorEventPayload.serializer(),
            bytes = frame.chat_event!!.payload.toByteArray(),
        )
    }

    private fun List<FrameEnvelope>.channelAddEvent(): ChatChannelAddEventPayload {
        val frame = first { it.chat_event?.event_type == ChatParityEventType.CHANNEL_ADD.wireName }
        return RewriteChatJson.decode(
            serializer = ChatChannelAddEventPayload.serializer(),
            bytes = frame.chat_event!!.payload.toByteArray(),
        )
    }

    private fun List<FrameEnvelope>.channelTextEvent(): ChatChannelTextEventPayload {
        val frame = first { it.chat_event?.event_type == ChatParityEventType.CHANNEL_TEXT.wireName }
        return RewriteChatJson.decode(
            serializer = ChatChannelTextEventPayload.serializer(),
            bytes = frame.chat_event!!.payload.toByteArray(),
        )
    }

    private fun List<FrameEnvelope>.whisperEvent(): ChatWhisperEventPayload {
        val frame = first { it.chat_event?.event_type == ChatParityEventType.WHISPER.wireName }
        return RewriteChatJson.decode(
            serializer = ChatWhisperEventPayload.serializer(),
            bytes = frame.chat_event!!.payload.toByteArray(),
        )
    }

    private suspend fun InMemoryClientConnection.channelAddEvent(): ChatChannelAddEventPayload {
        return RewriteChatJson.decode(
            serializer = ChatChannelAddEventPayload.serializer(),
            bytes = awaitFrame().chat_event!!.payload.toByteArray(),
        )
    }

    private suspend fun InMemoryClientConnection.channelTextEvent(): ChatChannelTextEventPayload {
        return RewriteChatJson.decode(
            serializer = ChatChannelTextEventPayload.serializer(),
            bytes = awaitFrame().chat_event!!.payload.toByteArray(),
        )
    }

    private suspend fun InMemoryClientConnection.whisperEvent(): ChatWhisperEventPayload {
        return RewriteChatJson.decode(
            serializer = ChatWhisperEventPayload.serializer(),
            bytes = awaitFrame().chat_event!!.payload.toByteArray(),
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val adapter: RewriteChatProtocolAdapter,
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
    private val messages: MutableList<PersistedChatMessage> = mutableListOf(),
    private val relations: MutableList<PersistedChatRelation> = mutableListOf(),
    private val channelMutes: MutableList<PersistedChannelMute> = mutableListOf(),
    private val presence: MutableList<PersistedChatPresence> = mutableListOf(),
) : ChatSocialRepository {
    override suspend fun upsertChannel(channel: PersistedChatChannel) {
        channels.removeAll { it.channelId == channel.channelId }
        channels += channel
    }

    override suspend fun listChannels(): List<PersistedChatChannel> = channels.sortedBy { it.createdAt }

    override suspend fun deleteChannel(channelId: String) {
        channels.removeAll { it.channelId == channelId }
        memberships.removeAll { it.channelId == channelId }
        messages.removeAll { it.channelId == channelId }
        channelMutes.removeAll { it.channelId == channelId }
    }

    override suspend fun upsertMembership(membership: PersistedChatChannelMembership) {
        memberships.removeAll { it.channelId == membership.channelId && it.playerId == membership.playerId }
        memberships += membership
    }

    override suspend fun listMemberships(channelId: String): List<PersistedChatChannelMembership> {
        return memberships.filter { it.channelId == channelId }.sortedBy { it.joinedAt }
    }

    override suspend fun deleteMembership(
        channelId: String,
        playerId: String,
    ) {
        memberships.removeAll { it.channelId == channelId && it.playerId == playerId }
    }

    override suspend fun appendMessage(message: PersistedChatMessage) {
        messages += message
    }

    override suspend fun loadChannelHistory(
        channelId: String,
        limit: Int,
    ): List<PersistedChatMessage> {
        return messages.filter { it.channelId == channelId }.sortedBy { it.createdAt }.take(limit)
    }

    suspend fun loadWhispers(): List<PersistedChatMessage> {
        return messages.filter { it.recipientPlayerId != null }.sortedBy { it.createdAt }
    }

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

    override suspend fun upsertChannelMute(mute: PersistedChannelMute) {
        channelMutes.removeAll {
            it.playerId == mute.playerId &&
                it.channelId == mute.channelId &&
                it.mutedPlayerId == mute.mutedPlayerId
        }
        channelMutes += mute
    }

    override suspend fun listChannelMutes(
        playerId: String,
        channelId: String,
    ): List<PersistedChannelMute> {
        return channelMutes.filter { it.playerId == playerId && it.channelId == channelId }
    }

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
