package com.hackwars.rewrite.chatcore

import com.hackwars.rewrite.protocol.ChatMessageKind
import com.hackwars.rewrite.protocol.ChatRelationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatContractsTest {
    @Test
    fun channelMessagesRequireChannelIdAndNoRecipient() {
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(
                messageId = MessageId("message-1"),
                messageKind = ChatMessageKind.CHANNEL,
                eventType = "channel_text",
                senderPlayerId = PlayerId("player-1"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ChatMessage(
                messageId = MessageId("message-1"),
                messageKind = ChatMessageKind.CHANNEL,
                eventType = "channel_text",
                senderPlayerId = PlayerId("player-1"),
                channelId = ChannelId("global"),
                recipientPlayerId = PlayerId("player-2"),
            )
        }
    }

    @Test
    fun whisperMessagesRequireRecipientAndNoChannel() {
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(
                messageId = MessageId("message-1"),
                messageKind = ChatMessageKind.WHISPER,
                eventType = "whisper",
                senderPlayerId = PlayerId("player-1"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ChatMessage(
                messageId = MessageId("message-1"),
                messageKind = ChatMessageKind.WHISPER,
                eventType = "whisper",
                senderPlayerId = PlayerId("player-1"),
                channelId = ChannelId("global"),
                recipientPlayerId = PlayerId("player-2"),
            )
        }
    }

    @Test
    fun relationGraphRejectsFriendIgnoreOverlap() {
        assertFailsWith<IllegalArgumentException> {
            RelationGraph(
                friends = setOf(PlayerId("player-2")),
                ignored = setOf(PlayerId("player-2")),
                mutedByChannel = emptyMap(),
            )
        }
    }

    @Test
    fun socialStateRejectsDuplicateIdentityKeys() {
        assertFailsWith<IllegalArgumentException> {
            ChatSocialState(
                channels = listOf(
                    ChatChannel(
                        channelId = ChannelId("global"),
                        displayName = "Global",
                        ownerPlayerId = PlayerId("player-1"),
                    ),
                    ChatChannel(
                        channelId = ChannelId("global"),
                        displayName = "Global Duplicate",
                        ownerPlayerId = PlayerId("player-1"),
                    ),
                ),
            )
        }
    }

    @Test
    fun socialStateRoundTripsThroughProtocolDescriptors() {
        val state = ChatSocialState(
            channels = listOf(
                ChatChannel(
                    channelId = ChannelId("global"),
                    displayName = "Global",
                    topic = "Main",
                    ownerPlayerId = PlayerId("player-1"),
                    memberCount = 2,
                ),
            ),
            memberships = listOf(
                ChatMembership(
                    channelId = ChannelId("global"),
                    playerId = PlayerId("player-1"),
                ),
            ),
            messages = listOf(
                ChatMessage(
                    messageId = MessageId("message-1"),
                    messageKind = ChatMessageKind.CHANNEL,
                    eventType = "channel_text",
                    senderPlayerId = PlayerId("player-1"),
                    body = "hello",
                    channelId = ChannelId("global"),
                ),
            ),
            relations = listOf(
                ChatRelation(
                    playerId = PlayerId("player-1"),
                    targetPlayerId = PlayerId("player-2"),
                    relationKind = ChatRelationKind.FRIEND,
                    comment = "ally",
                    online = true,
                ),
            ),
            channelMutes = listOf(
                ChatChannelMute(
                    playerId = PlayerId("player-1"),
                    channelId = ChannelId("global"),
                    mutedPlayerId = PlayerId("player-3"),
                ),
            ),
            presence = listOf(
                ChatPresence(
                    connectionId = ConnectionId("connection-1"),
                    playerId = PlayerId("player-1"),
                    onlineAtEpochMillis = 10L,
                    lastSeenAtEpochMillis = 11L,
                ),
            ),
        )

        val descriptor = state.toDescriptor()
        val roundTrip = ChatSocialState.fromDescriptor(descriptor)

        assertEquals(state, roundTrip)
    }

    @Test
    fun commandsRejectBlankOrSelfTargetedOperations() {
        assertFailsWith<IllegalArgumentException> {
            SendChannelTextCommand(
                actorPlayerId = PlayerId("player-1"),
                channelId = ChannelId("global"),
                message = "",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            UpsertRelationCommand(
                actorPlayerId = PlayerId("player-1"),
                targetPlayerId = PlayerId("player-1"),
                relationKind = ChatRelationKind.FRIEND,
            )
        }
    }
}
