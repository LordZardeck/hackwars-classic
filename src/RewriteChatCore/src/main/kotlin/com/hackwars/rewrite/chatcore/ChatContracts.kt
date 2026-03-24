package com.hackwars.rewrite.chatcore

@JvmInline
value class ChannelId(val value: String)

data class ChatSession(
    val connectionId: String,
    val playerId: String,
)

data class RelationGraph(
    val friends: Set<String>,
    val ignored: Set<String>,
    val mutedByChannel: Map<ChannelId, Set<String>>,
)

data class ChatEvent(
    val type: String,
    val channelId: ChannelId?,
    val payload: ByteArray,
)

interface ChatCommand {
    val name: String
}

interface ModerationRule {
    fun canKick(actor: String, target: String, channelId: ChannelId): Boolean
    fun canMute(actor: String, target: String, channelId: ChannelId): Boolean
}

interface ChatFanout {
    suspend fun publish(event: ChatEvent, recipients: Set<String>)
}
