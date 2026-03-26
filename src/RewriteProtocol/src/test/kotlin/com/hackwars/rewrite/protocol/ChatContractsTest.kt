package com.hackwars.rewrite.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ChatContractsTest {
    @Test
    fun chatJsonRoundTripsCanonicalSocialStateDescriptor() {
        val payload = ChatSocialStateDescriptor(
            channels = listOf(
                ChatChannelDescriptor(
                    channelId = "global",
                    displayName = "Global",
                    topic = "Main channel",
                    ownerPlayerId = "player-1",
                    privateChannel = false,
                    memberCount = 3,
                ),
            ),
            memberships = listOf(
                ChatChannelMembershipDescriptor(
                    channelId = "global",
                    playerId = "player-1",
                    role = ChatChannelRole.OWNER,
                ),
            ),
            messages = listOf(
                ChatMessageDescriptor(
                    messageId = "message-1",
                    messageKind = ChatMessageKind.CHANNEL,
                    eventType = "channel_text",
                    senderPlayerId = "player-1",
                    senderDisplayName = "Root",
                    body = "hello",
                    channelId = "global",
                    createdAtEpochMillis = 42L,
                ),
            ),
            relations = listOf(
                ChatRelationDescriptor(
                    playerId = "player-1",
                    targetPlayerId = "player-2",
                    relationKind = ChatRelationKind.FRIEND,
                    comment = "ally",
                    online = true,
                ),
            ),
            channelMutes = listOf(
                ChatChannelMuteDescriptor(
                    playerId = "player-1",
                    channelId = "global",
                    mutedPlayerId = "player-3",
                ),
            ),
            presence = listOf(
                ChatPresenceDescriptor(
                    connectionId = "connection-1",
                    playerId = "player-1",
                    onlineAtEpochMillis = 5L,
                    lastSeenAtEpochMillis = 6L,
                ),
            ),
        )

        val encoded = RewriteChatJson.encode(ChatSocialStateDescriptor.serializer(), payload)
        val decoded = RewriteChatJson.decode(ChatSocialStateDescriptor.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun chatJsonIgnoresUnknownFields() {
        val payload = """
            {
              "channels":[
                {
                  "channelId":"global",
                  "displayName":"Global",
                  "ownerPlayerId":"player-1",
                  "memberCount":2,
                  "ignored":"value"
                }
              ],
              "relations":[
                {
                  "playerId":"player-1",
                  "targetPlayerId":"player-2",
                  "relationKind":"IGNORED",
                  "online":false,
                  "ignoredNested":"value"
                }
              ],
              "ignoredRoot":"value"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = RewriteChatJson.decode(ChatSocialStateDescriptor.serializer(), payload)

        assertEquals("global", decoded.channels.single().channelId)
        assertEquals(ChatRelationKind.IGNORED, decoded.relations.single().relationKind)
        assertFalse(decoded.relations.single().online)
    }

    @Test
    fun chatCatalogMapsRetainedStableWireNames() {
        assertContentEquals(
            listOf(
                "sub_channels",
                "relation_list",
                "channel_text",
                "channel_text_me",
                "channel_join",
                "channel_create",
                "channel_leave",
                "whisper",
                "add_admin",
                "mute",
                "relation_add",
                "channel_kick",
            ),
            ChatRequestType.entries.map { it.wireName },
        )
        assertContentEquals(
            listOf(
                "channel_text",
                "channel_text_me",
                "channel_join",
                "channel_leave",
                "channel_add",
                "channel_remove",
                "channel_kick",
                "whisper",
                "sub_channels",
                "relation_list",
                "relation_add",
                "error",
            ),
            ChatParityEventType.entries.map { it.wireName },
        )

        assertEquals(ChatRequestType.CHANNEL_JOIN, ChatRequestType.fromWireName("channel_join"))
        assertEquals(ChatRequestType.RELATION_ADD, ChatRequestType.fromWireName("relation_add"))
        assertEquals(ChatParityEventType.CHANNEL_REMOVE, ChatParityEventType.fromWireName("channel_remove"))
        assertEquals(ChatParityEventType.ERROR, ChatParityEventType.fromWireName("error"))

        assertFailsWith<IllegalArgumentException> {
            ChatRequestType.fromWireName("missing")
        }
    }

    @Test
    fun chatRequestAndEventPayloadsRoundTripAcrossRetainedSurface() {
        fun <T> assertRoundTrip(
            serializer: kotlinx.serialization.KSerializer<T>,
            payload: T,
        ) {
            val encoded = RewriteChatJson.codec.encodeToString(serializer, payload)
            assertEquals(payload, RewriteChatJson.codec.decodeFromString(serializer, encoded))
        }

        assertRoundTrip(ChatSubChannelsPayload.serializer(), ChatSubChannelsPayload(senderPlayerId = "player-1"))
        assertRoundTrip(ChatRelationListPayload.serializer(), ChatRelationListPayload(senderPlayerId = "player-1"))
        assertRoundTrip(
            ChatChannelTextPayload.serializer(),
            ChatChannelTextPayload(
                senderPlayerId = "player-1",
                message = "hello",
                channelName = "General-0",
            ),
        )
        assertRoundTrip(
            ChatChannelTextMePayload.serializer(),
            ChatChannelTextMePayload(
                senderPlayerId = "player-1",
                message = "waves",
                channelName = "General-0",
            ),
        )
        assertRoundTrip(
            ChatChannelJoinPayload.serializer(),
            ChatChannelJoinPayload(
                senderPlayerId = "player-1",
                channelName = "Ops",
                password = "pw",
            ),
        )
        assertRoundTrip(
            ChatChannelCreatePayload.serializer(),
            ChatChannelCreatePayload(
                senderPlayerId = "player-1",
                channelName = "Ops",
                password = "pw",
            ),
        )
        assertRoundTrip(
            ChatChannelLeavePayload.serializer(),
            ChatChannelLeavePayload(
                senderPlayerId = "player-1",
                channelName = "Ops",
            ),
        )
        assertRoundTrip(
            ChatWhisperPayload.serializer(),
            ChatWhisperPayload(
                senderPlayerId = "player-1",
                receiverPlayerId = "player-2",
                message = "psst",
            ),
        )
        assertRoundTrip(
            ChatAddAdminPayload.serializer(),
            ChatAddAdminPayload(
                senderPlayerId = "player-1",
                channelName = "General-0",
                receiverPlayerId = "player-2",
            ),
        )
        assertRoundTrip(
            ChatMutePayload.serializer(),
            ChatMutePayload(
                senderPlayerId = "player-1",
                channelName = "General-0",
                receiverPlayerId = "player-3",
            ),
        )
        assertRoundTrip(
            ChatRelationAddPayload.serializer(),
            ChatRelationAddPayload(
                senderPlayerId = "player-1",
                relation = ChatRelationFlagsPayload(
                    targetPlayerId = "player-2",
                    comment = "ally",
                    friend = true,
                    ignore = false,
                    online = true,
                ),
            ),
        )
        assertRoundTrip(
            ChatChannelKickPayload.serializer(),
            ChatChannelKickPayload(
                senderPlayerId = "player-1",
                channelName = "General-0",
                targetPlayerId = "player-2",
            ),
        )

        assertRoundTrip(
            ChatChannelTextEventPayload.serializer(),
            ChatChannelTextEventPayload(
                receiverPlayerId = "player-1",
                channelName = "General-0",
                senderDisplayName = "Root",
                message = "hello",
            ),
        )
        assertRoundTrip(
            ChatChannelTextMeEventPayload.serializer(),
            ChatChannelTextMeEventPayload(
                receiverPlayerId = "player-1",
                channelName = "General-0",
                senderDisplayName = "Root",
                message = "waves",
            ),
        )
        assertRoundTrip(
            ChatChannelJoinEventPayload.serializer(),
            ChatChannelJoinEventPayload(
                receiverPlayerId = "player-1",
                roster = ChatChannelRosterPayload(
                    channelName = "General-0",
                    users = listOf("player-1", "player-2"),
                    adminUsers = setOf("player-1"),
                ),
            ),
        )
        assertRoundTrip(
            ChatChannelLeaveEventPayload.serializer(),
            ChatChannelLeaveEventPayload(
                receiverPlayerId = "player-1",
                channelName = "General-0",
            ),
        )
        assertRoundTrip(
            ChatChannelAddEventPayload.serializer(),
            ChatChannelAddEventPayload(
                receiverPlayerId = "player-1",
                channelName = "General-0",
                userToAdd = "player-2",
                admin = true,
            ),
        )
        assertRoundTrip(
            ChatChannelRemoveEventPayload.serializer(),
            ChatChannelRemoveEventPayload(
                receiverPlayerId = "player-1",
                channelName = "General-0",
                userToRemove = "player-2",
            ),
        )
        assertRoundTrip(
            ChatChannelKickEventPayload.serializer(),
            ChatChannelKickEventPayload(
                receiverPlayerId = "player-2",
                channelName = "General-0",
            ),
        )
        assertRoundTrip(
            ChatWhisperEventPayload.serializer(),
            ChatWhisperEventPayload(
                receiverPlayerId = "player-2",
                senderDisplayName = "Root",
                message = "psst",
            ),
        )
        assertRoundTrip(
            ChatSubChannelsEventPayload.serializer(),
            ChatSubChannelsEventPayload(
                receiverPlayerId = "player-1",
                channels = listOf(
                    ChatChannelRosterPayload(
                        channelName = "General-0",
                        users = listOf("player-1", "player-2"),
                        adminUsers = setOf("player-1"),
                    ),
                ),
            ),
        )
        assertRoundTrip(
            ChatRelationListEventPayload.serializer(),
            ChatRelationListEventPayload(
                receiverPlayerId = "player-1",
                relations = listOf(
                    ChatRelationFlagsPayload(
                        targetPlayerId = "player-2",
                        comment = "ally",
                        friend = true,
                        online = true,
                    ),
                ),
            ),
        )
        assertRoundTrip(
            ChatRelationAddEventPayload.serializer(),
            ChatRelationAddEventPayload(
                receiverPlayerId = "player-1",
                relation = ChatRelationFlagsPayload(
                    targetPlayerId = "player-2",
                    comment = "ally",
                    friend = true,
                    ignore = false,
                    online = true,
                ),
            ),
        )
        assertRoundTrip(
            ChatErrorEventPayload.serializer(),
            ChatErrorEventPayload(
                receiverPlayerId = "player-1",
                message = "boom",
            ),
        )
    }
}
