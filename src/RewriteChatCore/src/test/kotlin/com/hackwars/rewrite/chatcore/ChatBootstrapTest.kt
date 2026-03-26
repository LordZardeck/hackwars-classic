package com.hackwars.rewrite.chatcore

import com.hackwars.rewrite.protocol.ChatRelationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatBootstrapTest {
    @Test
    fun defaultAutoChannelsMatchRetainedLegacyOrder() {
        assertEquals(
            listOf("General-0", "Trade-0", "Help-0"),
            RetainedChatBootstrapPolicy.defaultAutoChannels,
        )
    }

    @Test
    fun bootstrapProjectionPreservesChannelOrderAndMergesRelationFlags() {
        val projection = RetainedChatBootstrapPolicy.project(
            receiverPlayerId = PlayerId("pf-localuser"),
            channelRosters = listOf(
                ChatChannelRoster(
                    channelId = ChannelId("General-0"),
                    users = listOf(PlayerId("pf-localuser"), PlayerId("alice")),
                    adminUsers = linkedSetOf(PlayerId("pf-localuser")),
                ),
                ChatChannelRoster(
                    channelId = ChannelId("Trade-0"),
                    users = listOf(PlayerId("pf-localuser")),
                ),
            ),
            relations = listOf(
                ChatRelation(
                    playerId = PlayerId("pf-localuser"),
                    targetPlayerId = PlayerId("alice"),
                    relationKind = ChatRelationKind.FRIEND,
                    comment = "raid partner",
                    online = true,
                ),
                ChatRelation(
                    playerId = PlayerId("pf-localuser"),
                    targetPlayerId = PlayerId("alice"),
                    relationKind = ChatRelationKind.IGNORED,
                ),
                ChatRelation(
                    playerId = PlayerId("pf-localuser"),
                    targetPlayerId = PlayerId("bob"),
                    relationKind = ChatRelationKind.IGNORED,
                    online = false,
                ),
            ),
        )

        assertEquals(
            listOf("General-0", "Trade-0"),
            projection.subscribedChannels.channels.map { it.channelName },
        )
        assertEquals(listOf("pf-localuser", "alice"), projection.subscribedChannels.channels.first().users)
        assertEquals(setOf("pf-localuser"), projection.subscribedChannels.channels.first().adminUsers)

        assertEquals(listOf("alice", "bob"), projection.relationList.relations.map { it.targetPlayerId })
        assertEquals("raid partner", projection.relationList.relations.first().comment)
        assertTrue(projection.relationList.relations.first().friend)
        assertTrue(projection.relationList.relations.first().ignore)
        assertTrue(projection.relationList.relations.first().online)
        assertFalse(projection.relationList.relations.last().friend)
        assertTrue(projection.relationList.relations.last().ignore)
    }
}
