package com.hackwars.rewrite.chatserver

import com.hackwars.rewrite.chatcore.ChannelId
import com.hackwars.rewrite.chatcore.ChatChannelRoster
import com.hackwars.rewrite.chatcore.ChatRelation
import com.hackwars.rewrite.chatcore.PlayerId
import com.hackwars.rewrite.chatcore.RetainedChatBootstrapPolicy
import com.hackwars.rewrite.persistence.AuthSessionRepository
import com.hackwars.rewrite.persistence.ChatSocialRepository
import com.hackwars.rewrite.persistence.PersistedChannelRole
import com.hackwars.rewrite.persistence.PersistedChatRelation
import com.hackwars.rewrite.persistence.PersistedChatPresence
import com.hackwars.rewrite.persistence.PersistedRelationKind
import com.hackwars.rewrite.persistence.PersistedServiceKind
import com.hackwars.rewrite.persistence.PersistedServiceSession
import com.hackwars.rewrite.protocol.ChatErrorEventPayload
import com.hackwars.rewrite.protocol.ChatParityEventType
import com.hackwars.rewrite.protocol.RewriteChatJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.protocol.VerifiedSession
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.CommandResponseStatus
import hackwars.rewrite.v1.ErrorEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

data class AuthenticatedChatSession(
    val connectionId: String,
    val playerId: String,
    val chatPrincipal: String,
    val playFabId: String,
    val playerIp: String,
    val sessionTicket: String,
    val heartbeatIntervalMillis: Long,
    val authenticatedAt: Instant,
) {
    init {
        require(connectionId.isNotBlank()) { "connectionId must not be blank." }
        require(playerId.isNotBlank()) { "playerId must not be blank." }
        require(chatPrincipal.isNotBlank()) { "chatPrincipal must not be blank." }
        require(playFabId.isNotBlank()) { "playFabId must not be blank." }
        require(playerIp.isNotBlank()) { "playerIp must not be blank." }
        require(sessionTicket.isNotBlank()) { "sessionTicket must not be blank." }
        require(heartbeatIntervalMillis >= 0L) { "heartbeatIntervalMillis must be non-negative." }
    }
}

class RewriteChatProtocolAdapter(
    private val authSessionRepository: AuthSessionRepository,
    private val chatSocialRepository: ChatSocialRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val activeSessions = linkedMapOf<String, AuthenticatedChatSession>()

    suspend fun bindSession(
        connectionId: String,
        verifiedSession: VerifiedSession,
    ): AuthenticatedChatSession? {
        val ticket = authSessionRepository.findSessionTicket(verifiedSession.sessionTicket)
            ?: return null
        if (ticket.playFabId != verifiedSession.playFabId || ticket.playerIp != verifiedSession.playerIp) {
            return null
        }

        val session = AuthenticatedChatSession(
            connectionId = connectionId,
            playerId = ticket.playerId,
            chatPrincipal = verifiedSession.playFabId.lowercase(),
            playFabId = verifiedSession.playFabId,
            playerIp = verifiedSession.playerIp,
            sessionTicket = verifiedSession.sessionTicket,
            heartbeatIntervalMillis = verifiedSession.heartbeatInterval.inWholeMilliseconds,
            authenticatedAt = verifiedSession.sessionStartedAt,
        )
        authSessionRepository.upsertServiceSession(
            PersistedServiceSession(
                serviceSessionId = "chat-$connectionId",
                serviceKind = PersistedServiceKind.CHAT,
                connectionId = connectionId,
                playerId = session.playerId,
                playFabId = session.playFabId,
                playerIp = session.playerIp,
                sessionTicket = session.sessionTicket,
                heartbeatIntervalMillis = session.heartbeatIntervalMillis,
                authenticatedAt = session.authenticatedAt,
                lastSeenAt = session.authenticatedAt,
                sessionPayload = RewriteChatJson.codec.encodeToString(
                    ChatServiceSessionPayload.serializer(),
                    ChatServiceSessionPayload(
                        playerId = session.playerId,
                        chatPrincipal = session.chatPrincipal,
                        playerIp = session.playerIp,
                    ),
                ),
            ),
        )
        activeSessions[connectionId] = session
        return session
    }

    suspend fun onSessionStarted(session: AuthenticatedChatSession): List<FrameEnvelope> {
        chatSocialRepository.upsertPresence(
            PersistedChatPresence(
                connectionId = session.connectionId,
                playerId = session.playerId,
                onlineAt = session.authenticatedAt,
                lastSeenAt = session.authenticatedAt,
                presencePayload = RewriteChatJson.codec.encodeToString(
                    ChatPresencePayload.serializer(),
                    ChatPresencePayload(
                        service = RewriteService.CHAT.name,
                        chatPrincipal = session.chatPrincipal,
                        playFabId = session.playFabId,
                        playerIp = session.playerIp,
                    ),
                ),
            ),
        )

        val bootstrap = RetainedChatBootstrapPolicy.project(
            receiverPlayerId = PlayerId(session.playerId),
            channelRosters = loadSubscribedChannelRosters(session.playerId),
            relations = loadRelations(session.playerId),
        )
        return listOf(
            RewriteFrames.chatEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = ChatParityEventType.SUB_CHANNELS,
                payload = RewriteChatJson.encode(
                    serializer = com.hackwars.rewrite.protocol.ChatSubChannelsEventPayload.serializer(),
                    value = bootstrap.subscribedChannels,
                ),
            ),
            RewriteFrames.chatEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = ChatParityEventType.RELATION_LIST,
                payload = RewriteChatJson.encode(
                    serializer = com.hackwars.rewrite.protocol.ChatRelationListEventPayload.serializer(),
                    value = bootstrap.relationList,
                ),
            ),
        )
    }

    suspend fun onSessionEnded(connectionId: String) {
        activeSessions.remove(connectionId)
        val now = clock()
        authSessionRepository.closeServiceSession(
            serviceKind = PersistedServiceKind.CHAT,
            connectionId = connectionId,
            closedAt = now,
        )
        chatSocialRepository.closePresence(
            connectionId = connectionId,
            offlineAt = now,
        )
    }

    suspend fun onCommand(
        connectionId: String,
        command: CommandEnvelope,
    ): List<FrameEnvelope> {
        if (activeSessions[connectionId] == null) {
            return listOf(
                errorResponse(
                    commandId = command.command_id,
                    code = "CHAT_SESSION_UNBOUND",
                    message = "CHAT session is not bound to a retained player identity.",
                ),
            )
        }
        return listOf(
            errorResponse(
                commandId = command.command_id,
                code = "CHAT_BOOTSTRAP_ONLY",
                message = "Retained chat command routing lands in RW-CHAT-003A and RW-CHAT-004A.",
            ),
        )
    }

    private suspend fun loadSubscribedChannelRosters(playerId: String): List<ChatChannelRoster> {
        return chatSocialRepository.listChannels().mapNotNull { channel ->
            val memberships = chatSocialRepository.listMemberships(channel.channelId)
            if (memberships.none { it.playerId == playerId }) {
                return@mapNotNull null
            }
            ChatChannelRoster(
                channelId = ChannelId(channel.channelId),
                users = memberships
                    .sortedBy { it.joinedAt }
                    .map { PlayerId(it.playerId) },
                adminUsers = memberships
                    .filter { it.role != PersistedChannelRole.MEMBER }
                    .mapTo(linkedSetOf()) { PlayerId(it.playerId) },
            )
        }
    }

    private suspend fun loadRelations(playerId: String): List<ChatRelation> {
        val onlinePlayerIds = chatSocialRepository.listActivePresence()
            .mapTo(linkedSetOf()) { it.playerId }
        return buildList {
            addAll(
                chatSocialRepository.listRelations(playerId, PersistedRelationKind.FRIEND)
                    .map { it.toDomainRelation(onlinePlayerIds, com.hackwars.rewrite.protocol.ChatRelationKind.FRIEND) },
            )
            addAll(
                chatSocialRepository.listRelations(playerId, PersistedRelationKind.IGNORED)
                    .map { it.toDomainRelation(onlinePlayerIds, com.hackwars.rewrite.protocol.ChatRelationKind.IGNORED) },
            )
        }
    }

    fun serviceAdapter(): RewriteServiceAdapter = RewriteChatServiceAdapter(this)

    private fun PersistedChatRelation.toDomainRelation(
        onlinePlayerIds: Set<String>,
        relationKind: com.hackwars.rewrite.protocol.ChatRelationKind,
    ): ChatRelation {
        return ChatRelation(
            playerId = PlayerId(playerId),
            targetPlayerId = PlayerId(targetPlayerId),
            relationKind = relationKind,
            comment = decodeRelationComment(relationPayload),
            online = targetPlayerId in onlinePlayerIds,
        )
    }

    private fun decodeRelationComment(payload: String): String {
        return runCatching {
            RewriteChatJson.codec.decodeFromString(ChatRelationPayload.serializer(), payload).comment
        }.getOrDefault("")
    }

    private companion object {
        fun errorResponse(
            commandId: String,
            code: String,
            message: String,
        ): FrameEnvelope {
            return RewriteFrames.commandResponse(
                commandId = commandId,
                status = CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR,
                error = ErrorEnvelope(
                    code = code,
                    message = message,
                    retryable = false,
                ),
            )
        }
    }
}

class RewriteChatServiceAdapter(
    private val adapter: RewriteChatProtocolAdapter,
) : RewriteServiceAdapter {
    override val service: RewriteService = RewriteService.CHAT

    override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
        val boundSession = adapter.bindSession(
            connectionId = session.connectionId,
            verifiedSession = session.verifiedSession,
        ) ?: return listOf(
            RewriteFrames.chatEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = ChatParityEventType.ERROR,
                payload = RewriteChatJson.encode(
                    serializer = ChatErrorEventPayload.serializer(),
                    value = ChatErrorEventPayload(
                        receiverPlayerId = session.verifiedSession.playFabId.lowercase(),
                        message = "CHAT auth could not be bound to a retained session ticket.",
                    ),
                ),
            ),
        )
        return adapter.onSessionStarted(boundSession)
    }

    override suspend fun onSessionEnded(session: InMemoryAuthenticatedSession) {
        adapter.onSessionEnded(session.connectionId)
    }

    override suspend fun onCommand(
        session: InMemoryAuthenticatedSession,
        command: CommandEnvelope,
    ): List<FrameEnvelope> {
        return adapter.onCommand(
            connectionId = session.connectionId,
            command = command,
        )
    }
}

@Serializable
private data class ChatServiceSessionPayload(
    val playerId: String,
    val chatPrincipal: String,
    val playerIp: String,
)

@Serializable
private data class ChatPresencePayload(
    val service: String,
    val chatPrincipal: String,
    val playFabId: String,
    val playerIp: String,
)

@Serializable
private data class ChatRelationPayload(
    val comment: String = "",
)
