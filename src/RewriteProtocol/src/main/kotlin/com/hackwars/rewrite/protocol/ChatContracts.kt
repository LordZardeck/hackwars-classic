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
enum class ChatRequestType(val wireName: String) {
    SUB_CHANNELS("sub_channels"),
    RELATION_LIST("relation_list"),
    CHANNEL_TEXT("channel_text"),
    CHANNEL_TEXT_ME("channel_text_me"),
    CHANNEL_JOIN("channel_join"),
    CHANNEL_CREATE("channel_create"),
    CHANNEL_LEAVE("channel_leave"),
    WHISPER("whisper"),
    ADD_ADMIN("add_admin"),
    MUTE("mute"),
    RELATION_ADD("relation_add"),
    CHANNEL_KICK("channel_kick"),
    ;

    companion object {
        fun fromWireName(wireName: String): ChatRequestType = entries.firstOrNull { it.wireName == wireName }
            ?: throw IllegalArgumentException("Unsupported chat request type: $wireName")
    }
}

@Serializable
enum class ChatParityEventType(val wireName: String) {
    CHANNEL_TEXT("channel_text"),
    CHANNEL_TEXT_ME("channel_text_me"),
    CHANNEL_JOIN("channel_join"),
    CHANNEL_LEAVE("channel_leave"),
    CHANNEL_ADD("channel_add"),
    CHANNEL_REMOVE("channel_remove"),
    CHANNEL_KICK("channel_kick"),
    WHISPER("whisper"),
    SUB_CHANNELS("sub_channels"),
    RELATION_LIST("relation_list"),
    RELATION_ADD("relation_add"),
    ERROR("error"),
    ;

    companion object {
        fun fromWireName(wireName: String): ChatParityEventType = entries.firstOrNull { it.wireName == wireName }
            ?: throw IllegalArgumentException("Unsupported chat parity event type: $wireName")
    }
}

@Serializable
data class ChatChannelRosterPayload(
    val channelName: String,
    val users: List<String> = emptyList(),
    val adminUsers: Set<String> = emptySet(),
)

@Serializable
data class ChatRelationFlagsPayload(
    val targetPlayerId: String,
    val comment: String = "",
    val friend: Boolean = false,
    val ignore: Boolean = false,
    val online: Boolean = false,
)

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

@Serializable
data class ChatSubChannelsPayload(
    val senderPlayerId: String,
)

@Serializable
data class ChatRelationListPayload(
    val senderPlayerId: String,
)

@Serializable
data class ChatChannelTextPayload(
    val senderPlayerId: String,
    val message: String,
    val channelName: String,
)

@Serializable
data class ChatChannelTextMePayload(
    val senderPlayerId: String,
    val message: String,
    val channelName: String,
)

@Serializable
data class ChatChannelJoinPayload(
    val senderPlayerId: String,
    val channelName: String,
    val password: String = "",
)

@Serializable
data class ChatChannelCreatePayload(
    val senderPlayerId: String,
    val channelName: String,
    val password: String = "",
)

@Serializable
data class ChatChannelLeavePayload(
    val senderPlayerId: String,
    val channelName: String,
)

@Serializable
data class ChatWhisperPayload(
    val senderPlayerId: String,
    val receiverPlayerId: String,
    val message: String,
)

@Serializable
data class ChatAddAdminPayload(
    val senderPlayerId: String,
    val channelName: String,
    val receiverPlayerId: String,
)

@Serializable
data class ChatMutePayload(
    val senderPlayerId: String,
    val channelName: String,
    val receiverPlayerId: String,
)

@Serializable
data class ChatRelationAddPayload(
    val senderPlayerId: String,
    val relation: ChatRelationFlagsPayload,
)

@Serializable
data class ChatChannelKickPayload(
    val senderPlayerId: String,
    val channelName: String,
    val targetPlayerId: String,
)

@Serializable
data class ChatChannelTextEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
    val senderDisplayName: String,
    val message: String,
)

@Serializable
data class ChatChannelTextMeEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
    val senderDisplayName: String,
    val message: String,
)

@Serializable
data class ChatChannelJoinEventPayload(
    val receiverPlayerId: String,
    val roster: ChatChannelRosterPayload,
)

@Serializable
data class ChatChannelLeaveEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
)

@Serializable
data class ChatChannelAddEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
    val userToAdd: String,
    val admin: Boolean = false,
)

@Serializable
data class ChatChannelRemoveEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
    val userToRemove: String,
)

@Serializable
data class ChatChannelKickEventPayload(
    val receiverPlayerId: String,
    val channelName: String,
)

@Serializable
data class ChatWhisperEventPayload(
    val receiverPlayerId: String,
    val senderDisplayName: String,
    val message: String,
)

@Serializable
data class ChatSubChannelsEventPayload(
    val receiverPlayerId: String,
    val channels: List<ChatChannelRosterPayload> = emptyList(),
)

@Serializable
data class ChatRelationListEventPayload(
    val receiverPlayerId: String,
    val relations: List<ChatRelationFlagsPayload> = emptyList(),
)

@Serializable
data class ChatRelationAddEventPayload(
    val receiverPlayerId: String,
    val relation: ChatRelationFlagsPayload,
)

@Serializable
data class ChatErrorEventPayload(
    val receiverPlayerId: String,
    val message: String,
)
