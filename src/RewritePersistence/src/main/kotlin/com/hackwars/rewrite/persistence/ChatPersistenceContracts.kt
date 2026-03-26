package com.hackwars.rewrite.persistence

import java.time.Instant

enum class PersistedChannelRole {
    OWNER,
    MODERATOR,
    MEMBER,
}

enum class PersistedChatMessageKind {
    CHANNEL,
    WHISPER,
}

enum class PersistedRelationKind {
    FRIEND,
    IGNORED,
}

data class PersistedChatChannel(
    val channelId: String,
    val displayName: String,
    val topic: String = "",
    val ownerPlayerId: String,
    val privateChannel: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val channelPayload: String = "{}",
)

data class PersistedChatChannelMembership(
    val channelId: String,
    val playerId: String,
    val role: PersistedChannelRole = PersistedChannelRole.MEMBER,
    val joinedAt: Instant = Instant.EPOCH,
    val membershipPayload: String = "{}",
)

data class PersistedChatMessage(
    val messageId: String,
    val messageKind: PersistedChatMessageKind,
    val eventType: String,
    val senderPlayerId: String,
    val createdAt: Instant = Instant.EPOCH,
    val payload: ByteArray = ByteArray(0),
    val channelId: String? = null,
    val recipientPlayerId: String? = null,
)

data class PersistedChatRelation(
    val playerId: String,
    val targetPlayerId: String,
    val relationKind: PersistedRelationKind,
    val createdAt: Instant = Instant.EPOCH,
    val relationPayload: String = "{}",
)

data class PersistedChannelMute(
    val playerId: String,
    val channelId: String,
    val mutedPlayerId: String,
    val createdAt: Instant = Instant.EPOCH,
    val mutePayload: String = "{}",
)

data class PersistedChatPresence(
    val connectionId: String,
    val playerId: String,
    val onlineAt: Instant = Instant.EPOCH,
    val lastSeenAt: Instant = onlineAt,
    val offlineAt: Instant? = null,
    val presencePayload: String = "{}",
)

data class SeedChatSocialSnapshot(
    val channels: List<PersistedChatChannel> = emptyList(),
    val memberships: List<PersistedChatChannelMembership> = emptyList(),
    val messages: List<PersistedChatMessage> = emptyList(),
    val relations: List<PersistedChatRelation> = emptyList(),
    val channelMutes: List<PersistedChannelMute> = emptyList(),
    val presence: List<PersistedChatPresence> = emptyList(),
) : SeedPayload

interface ChatSocialRepository {
    suspend fun upsertChannel(channel: PersistedChatChannel)

    suspend fun listChannels(): List<PersistedChatChannel>

    suspend fun deleteChannel(channelId: String)

    suspend fun upsertMembership(membership: PersistedChatChannelMembership)

    suspend fun listMemberships(channelId: String): List<PersistedChatChannelMembership>

    suspend fun deleteMembership(
        channelId: String,
        playerId: String,
    )

    suspend fun appendMessage(message: PersistedChatMessage)

    suspend fun loadChannelHistory(
        channelId: String,
        limit: Int = 100,
    ): List<PersistedChatMessage>

    suspend fun upsertRelation(relation: PersistedChatRelation)

    suspend fun listRelations(
        playerId: String,
        relationKind: PersistedRelationKind,
    ): List<PersistedChatRelation>

    suspend fun upsertChannelMute(mute: PersistedChannelMute)

    suspend fun listChannelMutes(
        playerId: String,
        channelId: String,
    ): List<PersistedChannelMute>

    suspend fun upsertPresence(presence: PersistedChatPresence)

    suspend fun listActivePresence(playerId: String? = null): List<PersistedChatPresence>

    suspend fun closePresence(
        connectionId: String,
        offlineAt: Instant,
    )
}
