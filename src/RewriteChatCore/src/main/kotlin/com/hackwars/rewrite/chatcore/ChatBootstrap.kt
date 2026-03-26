package com.hackwars.rewrite.chatcore

import com.hackwars.rewrite.protocol.ChatChannelRosterPayload
import com.hackwars.rewrite.protocol.ChatRelationFlagsPayload
import com.hackwars.rewrite.protocol.ChatRelationKind
import com.hackwars.rewrite.protocol.ChatRelationListEventPayload
import com.hackwars.rewrite.protocol.ChatSubChannelsEventPayload

data class ChatChannelRoster(
    val channelId: ChannelId,
    val users: List<PlayerId>,
    val adminUsers: Set<PlayerId> = emptySet(),
) {
    init {
        require(channelId.value.isNotBlank()) { "channelId must not be blank." }
        require(users.isNotEmpty()) { "users must not be empty." }
        users.forEach { require(it.value.isNotBlank()) { "users must not be blank." } }
        adminUsers.forEach { require(it.value.isNotBlank()) { "adminUsers must not be blank." } }
        require(adminUsers.all { it in users.toSet() }) { "adminUsers must be a subset of users." }
    }

    fun toPayload(): ChatChannelRosterPayload = ChatChannelRosterPayload(
        channelName = channelId.value,
        users = users.map { it.value },
        adminUsers = adminUsers.mapTo(linkedSetOf()) { it.value },
    )
}

data class ChatBootstrapProjection(
    val subscribedChannels: ChatSubChannelsEventPayload,
    val relationList: ChatRelationListEventPayload,
)

object RetainedChatBootstrapPolicy {
    val defaultAutoChannels: List<String> = listOf(
        "General-0",
        "Trade-0",
        "Help-0",
    )

    fun project(
        receiverPlayerId: PlayerId,
        channelRosters: List<ChatChannelRoster>,
        relations: Collection<ChatRelation>,
    ): ChatBootstrapProjection {
        require(receiverPlayerId.value.isNotBlank()) { "receiverPlayerId must not be blank." }
        return ChatBootstrapProjection(
            subscribedChannels = ChatSubChannelsEventPayload(
                receiverPlayerId = receiverPlayerId.value,
                channels = channelRosters.map(ChatChannelRoster::toPayload),
            ),
            relationList = ChatRelationListEventPayload(
                receiverPlayerId = receiverPlayerId.value,
                relations = mergeRelations(relations),
            ),
        )
    }

    private fun mergeRelations(relations: Collection<ChatRelation>): List<ChatRelationFlagsPayload> {
        return relations
            .groupBy { it.targetPlayerId.value }
            .toSortedMap()
            .map { (targetPlayerId, entries) ->
                ChatRelationFlagsPayload(
                    targetPlayerId = targetPlayerId,
                    comment = entries.firstNotNullOfOrNull { it.comment.takeIf(String::isNotBlank) }.orEmpty(),
                    friend = entries.any { it.relationKind == ChatRelationKind.FRIEND },
                    ignore = entries.any { it.relationKind == ChatRelationKind.IGNORED },
                    online = entries.any(ChatRelation::online),
                )
            }
    }
}
