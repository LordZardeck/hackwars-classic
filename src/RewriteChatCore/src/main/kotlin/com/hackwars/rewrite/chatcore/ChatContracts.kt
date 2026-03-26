package com.hackwars.rewrite.chatcore

import com.hackwars.rewrite.protocol.ChatChannelDescriptor
import com.hackwars.rewrite.protocol.ChatChannelMembershipDescriptor
import com.hackwars.rewrite.protocol.ChatChannelMuteDescriptor
import com.hackwars.rewrite.protocol.ChatChannelRole
import com.hackwars.rewrite.protocol.ChatCommandKind
import com.hackwars.rewrite.protocol.ChatMessageDescriptor
import com.hackwars.rewrite.protocol.ChatMessageKind
import com.hackwars.rewrite.protocol.ChatPresenceDescriptor
import com.hackwars.rewrite.protocol.ChatRelationDescriptor
import com.hackwars.rewrite.protocol.ChatRelationKind
import com.hackwars.rewrite.protocol.ChatSocialStateDescriptor

@JvmInline
value class ChannelId(val value: String)

@JvmInline
value class PlayerId(val value: String)

@JvmInline
value class ConnectionId(val value: String)

@JvmInline
value class MessageId(val value: String)

data class ChatSession(
    val connectionId: ConnectionId,
    val playerId: PlayerId,
) {
    init {
        connectionId.requireNotBlank("connectionId")
        playerId.requireNotBlank("playerId")
    }
}

data class ChatChannel(
    val channelId: ChannelId,
    val displayName: String,
    val topic: String = "",
    val ownerPlayerId: PlayerId,
    val privateChannel: Boolean = false,
    val memberCount: Int = 0,
) {
    init {
        channelId.requireNotBlank("channelId")
        require(displayName.isNotBlank()) { "displayName must not be blank." }
        ownerPlayerId.requireNotBlank("ownerPlayerId")
        require(memberCount >= 0) { "memberCount must be non-negative." }
    }

    fun toDescriptor(): ChatChannelDescriptor = ChatChannelDescriptor(
        channelId = channelId.value,
        displayName = displayName,
        topic = topic,
        ownerPlayerId = ownerPlayerId.value,
        privateChannel = privateChannel,
        memberCount = memberCount,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatChannelDescriptor): ChatChannel = ChatChannel(
            channelId = ChannelId(descriptor.channelId),
            displayName = descriptor.displayName,
            topic = descriptor.topic,
            ownerPlayerId = PlayerId(descriptor.ownerPlayerId),
            privateChannel = descriptor.privateChannel,
            memberCount = descriptor.memberCount,
        )
    }
}

data class ChatMembership(
    val channelId: ChannelId,
    val playerId: PlayerId,
    val role: ChatChannelRole = ChatChannelRole.MEMBER,
) {
    init {
        channelId.requireNotBlank("channelId")
        playerId.requireNotBlank("playerId")
    }

    fun toDescriptor(): ChatChannelMembershipDescriptor = ChatChannelMembershipDescriptor(
        channelId = channelId.value,
        playerId = playerId.value,
        role = role,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatChannelMembershipDescriptor): ChatMembership = ChatMembership(
            channelId = ChannelId(descriptor.channelId),
            playerId = PlayerId(descriptor.playerId),
            role = descriptor.role,
        )
    }
}

data class ChatMessage(
    val messageId: MessageId,
    val messageKind: ChatMessageKind,
    val eventType: String,
    val senderPlayerId: PlayerId,
    val senderDisplayName: String = "",
    val body: String = "",
    val channelId: ChannelId? = null,
    val recipientPlayerId: PlayerId? = null,
    val createdAtEpochMillis: Long = 0L,
) {
    init {
        messageId.requireNotBlank("messageId")
        require(eventType.isNotBlank()) { "eventType must not be blank." }
        senderPlayerId.requireNotBlank("senderPlayerId")
        require(createdAtEpochMillis >= 0L) { "createdAtEpochMillis must be non-negative." }
        when (messageKind) {
            ChatMessageKind.CHANNEL -> {
                require(channelId != null) { "Channel messages require channelId." }
                require(recipientPlayerId == null) { "Channel messages must not define recipientPlayerId." }
                channelId.requireNotBlank("channelId")
            }

            ChatMessageKind.WHISPER -> {
                require(recipientPlayerId != null) { "Whisper messages require recipientPlayerId." }
                require(channelId == null) { "Whisper messages must not define channelId." }
                recipientPlayerId.requireNotBlank("recipientPlayerId")
            }
        }
    }

    fun toDescriptor(): ChatMessageDescriptor = ChatMessageDescriptor(
        messageId = messageId.value,
        messageKind = messageKind,
        eventType = eventType,
        senderPlayerId = senderPlayerId.value,
        senderDisplayName = senderDisplayName,
        body = body,
        channelId = channelId?.value,
        recipientPlayerId = recipientPlayerId?.value,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatMessageDescriptor): ChatMessage = ChatMessage(
            messageId = MessageId(descriptor.messageId),
            messageKind = descriptor.messageKind,
            eventType = descriptor.eventType,
            senderPlayerId = PlayerId(descriptor.senderPlayerId),
            senderDisplayName = descriptor.senderDisplayName,
            body = descriptor.body,
            channelId = descriptor.channelId?.let(::ChannelId),
            recipientPlayerId = descriptor.recipientPlayerId?.let(::PlayerId),
            createdAtEpochMillis = descriptor.createdAtEpochMillis,
        )
    }
}

data class ChatRelation(
    val playerId: PlayerId,
    val targetPlayerId: PlayerId,
    val relationKind: ChatRelationKind,
    val comment: String = "",
    val online: Boolean = false,
) {
    init {
        playerId.requireNotBlank("playerId")
        targetPlayerId.requireNotBlank("targetPlayerId")
        require(playerId != targetPlayerId) { "playerId and targetPlayerId must differ." }
    }

    fun toDescriptor(): ChatRelationDescriptor = ChatRelationDescriptor(
        playerId = playerId.value,
        targetPlayerId = targetPlayerId.value,
        relationKind = relationKind,
        comment = comment,
        online = online,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatRelationDescriptor): ChatRelation = ChatRelation(
            playerId = PlayerId(descriptor.playerId),
            targetPlayerId = PlayerId(descriptor.targetPlayerId),
            relationKind = descriptor.relationKind,
            comment = descriptor.comment,
            online = descriptor.online,
        )
    }
}

data class ChatChannelMute(
    val playerId: PlayerId,
    val channelId: ChannelId,
    val mutedPlayerId: PlayerId,
) {
    init {
        playerId.requireNotBlank("playerId")
        channelId.requireNotBlank("channelId")
        mutedPlayerId.requireNotBlank("mutedPlayerId")
        require(playerId != mutedPlayerId) { "playerId and mutedPlayerId must differ." }
    }

    fun toDescriptor(): ChatChannelMuteDescriptor = ChatChannelMuteDescriptor(
        playerId = playerId.value,
        channelId = channelId.value,
        mutedPlayerId = mutedPlayerId.value,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatChannelMuteDescriptor): ChatChannelMute = ChatChannelMute(
            playerId = PlayerId(descriptor.playerId),
            channelId = ChannelId(descriptor.channelId),
            mutedPlayerId = PlayerId(descriptor.mutedPlayerId),
        )
    }
}

data class ChatPresence(
    val connectionId: ConnectionId,
    val playerId: PlayerId,
    val onlineAtEpochMillis: Long = 0L,
    val lastSeenAtEpochMillis: Long = onlineAtEpochMillis,
    val offlineAtEpochMillis: Long? = null,
) {
    init {
        connectionId.requireNotBlank("connectionId")
        playerId.requireNotBlank("playerId")
        require(onlineAtEpochMillis >= 0L) { "onlineAtEpochMillis must be non-negative." }
        require(lastSeenAtEpochMillis >= onlineAtEpochMillis) { "lastSeenAtEpochMillis must be >= onlineAtEpochMillis." }
        require(offlineAtEpochMillis == null || offlineAtEpochMillis >= lastSeenAtEpochMillis) {
            "offlineAtEpochMillis must be null or >= lastSeenAtEpochMillis."
        }
    }

    fun toDescriptor(): ChatPresenceDescriptor = ChatPresenceDescriptor(
        connectionId = connectionId.value,
        playerId = playerId.value,
        onlineAtEpochMillis = onlineAtEpochMillis,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        offlineAtEpochMillis = offlineAtEpochMillis,
    )

    companion object {
        fun fromDescriptor(descriptor: ChatPresenceDescriptor): ChatPresence = ChatPresence(
            connectionId = ConnectionId(descriptor.connectionId),
            playerId = PlayerId(descriptor.playerId),
            onlineAtEpochMillis = descriptor.onlineAtEpochMillis,
            lastSeenAtEpochMillis = descriptor.lastSeenAtEpochMillis,
            offlineAtEpochMillis = descriptor.offlineAtEpochMillis,
        )
    }
}

data class ChatSocialState(
    val channels: List<ChatChannel> = emptyList(),
    val memberships: List<ChatMembership> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val relations: List<ChatRelation> = emptyList(),
    val channelMutes: List<ChatChannelMute> = emptyList(),
    val presence: List<ChatPresence> = emptyList(),
) {
    init {
        channels.requireDistinct("channelId") { it.channelId.value }
        memberships.requireDistinct("channel membership") { "${it.channelId.value}:${it.playerId.value}" }
        messages.requireDistinct("messageId") { it.messageId.value }
        relations.requireDistinct("relation") { "${it.playerId.value}:${it.targetPlayerId.value}:${it.relationKind}" }
        channelMutes.requireDistinct("channel mute") { "${it.playerId.value}:${it.channelId.value}:${it.mutedPlayerId.value}" }
        presence.requireDistinct("connectionId") { it.connectionId.value }
    }

    fun toDescriptor(): ChatSocialStateDescriptor = ChatSocialStateDescriptor(
        channels = channels.map(ChatChannel::toDescriptor),
        memberships = memberships.map(ChatMembership::toDescriptor),
        messages = messages.map(ChatMessage::toDescriptor),
        relations = relations.map(ChatRelation::toDescriptor),
        channelMutes = channelMutes.map(ChatChannelMute::toDescriptor),
        presence = presence.map(ChatPresence::toDescriptor),
    )

    companion object {
        fun fromDescriptor(descriptor: ChatSocialStateDescriptor): ChatSocialState = ChatSocialState(
            channels = descriptor.channels.map(ChatChannel::fromDescriptor),
            memberships = descriptor.memberships.map(ChatMembership::fromDescriptor),
            messages = descriptor.messages.map(ChatMessage::fromDescriptor),
            relations = descriptor.relations.map(ChatRelation::fromDescriptor),
            channelMutes = descriptor.channelMutes.map(ChatChannelMute::fromDescriptor),
            presence = descriptor.presence.map(ChatPresence::fromDescriptor),
        )
    }
}

data class RelationGraph(
    val friends: Set<PlayerId>,
    val ignored: Set<PlayerId>,
    val mutedByChannel: Map<ChannelId, Set<PlayerId>>,
) {
    init {
        require(friends.intersect(ignored).isEmpty()) { "friends and ignored must not overlap." }
    }

    companion object {
        fun from(
            relations: Collection<ChatRelation>,
            channelMutes: Collection<ChatChannelMute>,
        ): RelationGraph = RelationGraph(
            friends = relations.filter { it.relationKind == ChatRelationKind.FRIEND }.mapTo(linkedSetOf()) { it.targetPlayerId },
            ignored = relations.filter { it.relationKind == ChatRelationKind.IGNORED }.mapTo(linkedSetOf()) { it.targetPlayerId },
            mutedByChannel = channelMutes.groupBy { it.channelId }.mapValues { (_, mutes) ->
                mutes.mapTo(linkedSetOf()) { it.mutedPlayerId }
            },
        )
    }
}

class ChatEvent(
    val type: String,
    val channelId: ChannelId?,
    val payload: ByteArray,
) {
    init {
        require(type.isNotBlank()) { "type must not be blank." }
        channelId?.requireNotBlank("channelId")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatEvent) return false

        return type == other.type &&
            channelId == other.channelId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (channelId?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "ChatEvent(type=$type, channelId=$channelId, payloadSize=${payload.size})"
    }
}

sealed interface ChatCommand {
    val kind: ChatCommandKind
    val actorPlayerId: PlayerId
}

data class CreateChannelCommand(
    override val actorPlayerId: PlayerId,
    val displayName: String,
    val password: String = "",
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.CREATE_CHANNEL

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        require(displayName.isNotBlank()) { "displayName must not be blank." }
    }
}

data class JoinChannelCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val password: String = "",
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.JOIN_CHANNEL

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
    }
}

data class LeaveChannelCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.LEAVE_CHANNEL

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
    }
}

data class SendChannelTextCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val message: String,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.SEND_CHANNEL_TEXT

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
        require(message.isNotBlank()) { "message must not be blank." }
    }
}

data class SendChannelEmoteCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val message: String,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.SEND_CHANNEL_EMOTE

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
        require(message.isNotBlank()) { "message must not be blank." }
    }
}

data class WhisperCommand(
    override val actorPlayerId: PlayerId,
    val recipientPlayerId: PlayerId,
    val message: String,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.WHISPER

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        recipientPlayerId.requireNotBlank("recipientPlayerId")
        require(message.isNotBlank()) { "message must not be blank." }
    }
}

data class UpsertRelationCommand(
    override val actorPlayerId: PlayerId,
    val targetPlayerId: PlayerId,
    val relationKind: ChatRelationKind,
    val comment: String = "",
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.UPSERT_RELATION

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        targetPlayerId.requireNotBlank("targetPlayerId")
        require(actorPlayerId != targetPlayerId) { "actorPlayerId and targetPlayerId must differ." }
    }
}

data class ListRelationsCommand(
    override val actorPlayerId: PlayerId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.LIST_RELATIONS

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
    }
}

data class ListChannelsCommand(
    override val actorPlayerId: PlayerId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.LIST_CHANNELS

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
    }
}

data class KickChannelMemberCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val targetPlayerId: PlayerId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.KICK_CHANNEL_MEMBER

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
        targetPlayerId.requireNotBlank("targetPlayerId")
        require(actorPlayerId != targetPlayerId) { "actorPlayerId and targetPlayerId must differ." }
    }
}

data class AddChannelModeratorCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val targetPlayerId: PlayerId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.ADD_CHANNEL_MODERATOR

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
        targetPlayerId.requireNotBlank("targetPlayerId")
        require(actorPlayerId != targetPlayerId) { "actorPlayerId and targetPlayerId must differ." }
    }
}

data class MuteChannelMemberCommand(
    override val actorPlayerId: PlayerId,
    val channelId: ChannelId,
    val targetPlayerId: PlayerId,
) : ChatCommand {
    override val kind: ChatCommandKind = ChatCommandKind.MUTE_CHANNEL_MEMBER

    init {
        actorPlayerId.requireNotBlank("actorPlayerId")
        channelId.requireNotBlank("channelId")
        targetPlayerId.requireNotBlank("targetPlayerId")
        require(actorPlayerId != targetPlayerId) { "actorPlayerId and targetPlayerId must differ." }
    }
}

interface ModerationRule {
    fun canKick(actor: PlayerId, target: PlayerId, channel: ChatChannel): Boolean
    fun canMute(actor: PlayerId, target: PlayerId, channel: ChatChannel): Boolean
}

interface ChatFanout {
    suspend fun publish(event: ChatEvent, recipients: Set<PlayerId>)
}

private fun ChannelId.requireNotBlank(label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
}

private fun PlayerId.requireNotBlank(label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
}

private fun ConnectionId.requireNotBlank(label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
}

private fun MessageId.requireNotBlank(label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
}

private fun <T, K> Iterable<T>.requireDistinct(
    label: String,
    keySelector: (T) -> K,
) {
    val keys = map(keySelector)
    require(keys.size == keys.toSet().size) { "$label entries must be unique." }
}
