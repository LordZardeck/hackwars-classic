package com.hackwars.rewrite.protocol

import kotlin.test.Test
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
    fun chatCatalogMapsStableWireNames() {
        assertEquals(ChatRequestType.CHANNEL_JOIN, ChatRequestType.fromWireName("channel_join"))
        assertEquals(ChatRequestType.RELATION_ADD, ChatRequestType.fromWireName("relation_add"))
        assertEquals(ChatParityEventType.CHANNEL_REMOVE, ChatParityEventType.fromWireName("channel_remove"))
        assertEquals(ChatParityEventType.ERROR, ChatParityEventType.fromWireName("error"))

        assertFailsWith<IllegalArgumentException> {
            ChatRequestType.fromWireName("missing")
        }
    }

    @Test
    fun chatRequestAndEventPayloadsRoundTrip() {
        val request = ChatRelationAddPayload(
            senderPlayerId = "player-1",
            relation = ChatRelationFlagsPayload(
                targetPlayerId = "player-2",
                comment = "ally",
                friend = true,
                ignore = false,
                online = true,
            ),
        )
        val event = ChatSubChannelsEventPayload(
            receiverPlayerId = "player-1",
            channels = listOf(
                ChatChannelRosterPayload(
                    channelName = "General",
                    users = listOf("player-1", "player-2"),
                    adminUsers = setOf("player-1"),
                ),
            ),
        )

        val encodedRequest = RewriteChatJson.encode(ChatRelationAddPayload.serializer(), request)
        val encodedEvent = RewriteChatJson.encode(ChatSubChannelsEventPayload.serializer(), event)

        assertEquals(request, RewriteChatJson.decode(ChatRelationAddPayload.serializer(), encodedRequest))
        assertEquals(event, RewriteChatJson.decode(ChatSubChannelsEventPayload.serializer(), encodedEvent))
    }
}
