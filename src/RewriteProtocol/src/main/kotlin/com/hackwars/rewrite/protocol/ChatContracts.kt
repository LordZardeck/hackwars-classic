package com.hackwars.rewrite.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RewriteChatJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = codec.encodeToString(serializer, value).encodeToByteArray()

    fun <T> decode(
        serializer: KSerializer<T>,
        bytes: ByteArray,
    ): T = codec.decodeFromString(serializer, bytes.decodeToString())
}

@Serializable
enum class ChatChannelRole {
    OWNER,
    MODERATOR,
    MEMBER,
}

@Serializable
enum class ChatMessageKind {
    CHANNEL,
    WHISPER,
}

@Serializable
enum class ChatRelationKind {
    FRIEND,
    IGNORED,
}

@Serializable
enum class ChatCommandKind {
    CREATE_CHANNEL,
    JOIN_CHANNEL,
    LEAVE_CHANNEL,
    SEND_CHANNEL_TEXT,
    SEND_CHANNEL_EMOTE,
    WHISPER,
    UPSERT_RELATION,
    LIST_RELATIONS,
    LIST_CHANNELS,
    KICK_CHANNEL_MEMBER,
    ADD_CHANNEL_MODERATOR,
    MUTE_CHANNEL_MEMBER,
}

@Serializable
data class ChatChannelDescriptor(
    val channelId: String,
    val displayName: String,
    val topic: String = "",
    val ownerPlayerId: String,
    val privateChannel: Boolean = false,
    val memberCount: Int = 0,
)

@Serializable
data class ChatChannelMembershipDescriptor(
    val channelId: String,
    val playerId: String,
    val role: ChatChannelRole = ChatChannelRole.MEMBER,
)

@Serializable
data class ChatMessageDescriptor(
    val messageId: String,
    val messageKind: ChatMessageKind,
    val eventType: String,
    val senderPlayerId: String,
    val senderDisplayName: String = "",
    val body: String = "",
    val channelId: String? = null,
    val recipientPlayerId: String? = null,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
data class ChatRelationDescriptor(
    val playerId: String,
    val targetPlayerId: String,
    val relationKind: ChatRelationKind,
    val comment: String = "",
    val online: Boolean = false,
)

@Serializable
data class ChatChannelMuteDescriptor(
    val playerId: String,
    val channelId: String,
    val mutedPlayerId: String,
)

@Serializable
data class ChatPresenceDescriptor(
    val connectionId: String,
    val playerId: String,
    val onlineAtEpochMillis: Long = 0L,
    val lastSeenAtEpochMillis: Long = onlineAtEpochMillis,
    val offlineAtEpochMillis: Long? = null,
)

@Serializable
data class ChatSocialStateDescriptor(
    val channels: List<ChatChannelDescriptor> = emptyList(),
    val memberships: List<ChatChannelMembershipDescriptor> = emptyList(),
    val messages: List<ChatMessageDescriptor> = emptyList(),
    val relations: List<ChatRelationDescriptor> = emptyList(),
    val channelMutes: List<ChatChannelMuteDescriptor> = emptyList(),
    val presence: List<ChatPresenceDescriptor> = emptyList(),
)
