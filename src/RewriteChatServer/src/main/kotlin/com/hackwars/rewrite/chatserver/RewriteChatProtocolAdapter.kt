package com.hackwars.rewrite.chatserver

import com.hackwars.rewrite.chatcore.ChannelId
import com.hackwars.rewrite.chatcore.ChatChannelRoster
import com.hackwars.rewrite.chatcore.ChatRelation
import com.hackwars.rewrite.chatcore.PlayerId
import com.hackwars.rewrite.chatcore.RetainedChatBootstrapPolicy
import com.hackwars.rewrite.persistence.AuthSessionRepository
import com.hackwars.rewrite.persistence.ChatSocialRepository
import com.hackwars.rewrite.persistence.PersistedChannelRole
import com.hackwars.rewrite.persistence.PersistedChannelMute
import com.hackwars.rewrite.persistence.PersistedChatChannel
import com.hackwars.rewrite.persistence.PersistedChatChannelMembership
import com.hackwars.rewrite.persistence.PersistedChatRelation
import com.hackwars.rewrite.persistence.PersistedChatPresence
import com.hackwars.rewrite.persistence.PersistedRelationKind
import com.hackwars.rewrite.persistence.PersistedServiceKind
import com.hackwars.rewrite.persistence.PersistedServiceSession
import com.hackwars.rewrite.protocol.ChatAddAdminPayload
import com.hackwars.rewrite.protocol.ChatChannelCreatePayload
import com.hackwars.rewrite.protocol.ChatChannelJoinPayload
import com.hackwars.rewrite.protocol.ChatChannelKickPayload
import com.hackwars.rewrite.protocol.ChatChannelLeavePayload
import com.hackwars.rewrite.protocol.ChatErrorEventPayload
import com.hackwars.rewrite.protocol.ChatMutePayload
import com.hackwars.rewrite.protocol.ChatParityEventType
import com.hackwars.rewrite.protocol.ChatRequestType
import com.hackwars.rewrite.protocol.ChatSubChannelsPayload
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
    private var pushFrame: suspend (connectionId: String, frame: FrameEnvelope) -> Unit = { _, _ -> }

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
        ensureDefaultAutoChannels(session)

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
        val session = activeSessions[connectionId]
        if (session == null) {
            return listOf(
                errorResponse(
                    commandId = command.command_id,
                    code = "CHAT_SESSION_UNBOUND",
                    message = "CHAT session is not bound to a retained player identity.",
                ),
            )
        }
        val requestType = runCatching { ChatRequestType.fromWireName(command.command_name) }
            .getOrElse {
                return requestErrorFrames(
                        commandId = command.command_id,
                        receiverPlayerId = session.playerId,
                        code = "CHAT_REQUEST_UNSUPPORTED",
                        message = "Unsupported retained chat request `${command.command_name}`.",
                    )
            }
        return when (requestType) {
            ChatRequestType.SUB_CHANNELS -> decodeAndHandle<ChatSubChannelsPayload>(
                command = command,
                session = session,
                serializer = ChatSubChannelsPayload.serializer(),
            ) { payload ->
                if (payload.senderPlayerId != session.playerId) {
                    requestErrorFrames(
                        commandId = command.command_id,
                        receiverPlayerId = session.playerId,
                        code = "CHAT_SENDER_MISMATCH",
                        message = "Retained chat sender identity must match the authenticated player.",
                    )
                } else {
                    responseWithSubscribedChannelsRefresh(
                        commandId = command.command_id,
                        session = session,
                    )
                }
            }

            ChatRequestType.CHANNEL_CREATE -> decodeAndHandle<ChatChannelCreatePayload>(
                command = command,
                session = session,
                serializer = ChatChannelCreatePayload.serializer(),
            ) { payload ->
                handleCreateChannel(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.CHANNEL_JOIN -> decodeAndHandle<ChatChannelJoinPayload>(
                command = command,
                session = session,
                serializer = ChatChannelJoinPayload.serializer(),
            ) { payload ->
                handleJoinChannel(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.CHANNEL_LEAVE -> decodeAndHandle<ChatChannelLeavePayload>(
                command = command,
                session = session,
                serializer = ChatChannelLeavePayload.serializer(),
            ) { payload ->
                handleLeaveChannel(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.CHANNEL_KICK -> decodeAndHandle<ChatChannelKickPayload>(
                command = command,
                session = session,
                serializer = ChatChannelKickPayload.serializer(),
            ) { payload ->
                handleKickChannelMember(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.ADD_ADMIN -> decodeAndHandle<ChatAddAdminPayload>(
                command = command,
                session = session,
                serializer = ChatAddAdminPayload.serializer(),
            ) { payload ->
                handleAddChannelModerator(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.MUTE -> decodeAndHandle<ChatMutePayload>(
                command = command,
                session = session,
                serializer = ChatMutePayload.serializer(),
            ) { payload ->
                handleMuteChannelMember(
                    commandId = command.command_id,
                    session = session,
                    payload = payload,
                )
            }

            ChatRequestType.CHANNEL_TEXT,
            ChatRequestType.CHANNEL_TEXT_ME,
            ChatRequestType.WHISPER,
            -> requestErrorFrames(
                    commandId = command.command_id,
                    receiverPlayerId = session.playerId,
                    code = "CHAT_REQUEST_OWNED_BY_RW_CHAT_003B",
                    message = "Retained `${requestType.wireName}` lands in RW-CHAT-003B.",
                )

            ChatRequestType.RELATION_LIST,
            ChatRequestType.RELATION_ADD,
            -> requestErrorFrames(
                    commandId = command.command_id,
                    receiverPlayerId = session.playerId,
                    code = "CHAT_REQUEST_OWNED_BY_RW_CHAT_004A",
                    message = "Retained `${requestType.wireName}` lands in RW-CHAT-004A.",
                )
        }
    }

    private suspend fun ensureDefaultAutoChannels(session: AuthenticatedChatSession) {
        val now = clock()
        for (channelId in RetainedChatBootstrapPolicy.defaultAutoChannels) {
            val channel = loadChannel(channelId)
                ?: PersistedChatChannel(
                    channelId = channelId,
                    displayName = channelId,
                    ownerPlayerId = session.playerId,
                    createdAt = now,
                    channelPayload = encodeChannelPolicy(
                        ChannelPolicyPayload(
                            kind = "auto",
                            adminCanKick = false,
                            removeWhenEmpty = false,
                        ),
                    ),
                ).also { chatSocialRepository.upsertChannel(it) }
            val memberships = chatSocialRepository.listMemberships(channel.channelId)
            if (memberships.none { it.playerId == session.playerId }) {
                val role = if (memberships.isEmpty()) PersistedChannelRole.OWNER else PersistedChannelRole.MEMBER
                if (role == PersistedChannelRole.OWNER && channel.ownerPlayerId != session.playerId) {
                    chatSocialRepository.upsertChannel(channel.copy(ownerPlayerId = session.playerId))
                }
                chatSocialRepository.upsertMembership(
                    PersistedChatChannelMembership(
                        channelId = channel.channelId,
                        playerId = session.playerId,
                        role = role,
                        joinedAt = now,
                        membershipPayload = encodeMembershipPayload(
                            MembershipPayload(
                                source = "auto_bootstrap",
                                grantedBy = session.playerId,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun handleCreateChannel(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatChannelCreatePayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }
        validatePassword(payload.password)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_PASSWORD", it) }

        val existing = loadChannel(payload.channelName)
        if (existing != null) {
            val existingError = errorEvent(
                receiverPlayerId = session.playerId,
                message = "Channel ${payload.channelName} already exists. Joining instead.",
            )
            val joinedFrames = handleJoinChannel(
                commandId = commandId,
                session = session,
                payload = ChatChannelJoinPayload(
                    senderPlayerId = payload.senderPlayerId,
                    channelName = payload.channelName,
                    password = payload.password,
                ),
            )
            return if (joinedFrames.firstOrNull()?.command_response?.status == CommandResponseStatus.COMMAND_RESPONSE_STATUS_OK) {
                listOf(joinedFrames.first(), existingError) + joinedFrames.drop(1)
            } else {
                listOf(existingError) + joinedFrames
            }
        }

        val existingMemberships = loadMembershipsForPlayer(session.playerId)
        if (existingMemberships.size >= MAX_CHANNELS_PER_PLAYER) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MAX_CHANNELS_REACHED",
                message = "Retained chat only allows $MAX_CHANNELS_PER_PLAYER subscribed channels.",
            )
        }

        val now = clock()
        chatSocialRepository.upsertChannel(
            PersistedChatChannel(
                channelId = payload.channelName,
                displayName = payload.channelName,
                ownerPlayerId = session.playerId,
                privateChannel = payload.password.isNotBlank(),
                createdAt = now,
                channelPayload = encodeChannelPolicy(
                    ChannelPolicyPayload(
                        kind = "user",
                        password = payload.password,
                        adminCanKick = true,
                        removeWhenEmpty = true,
                    ),
                ),
            ),
        )
        chatSocialRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = payload.channelName,
                playerId = session.playerId,
                role = PersistedChannelRole.OWNER,
                joinedAt = now,
                membershipPayload = encodeMembershipPayload(
                    MembershipPayload(
                        source = "create",
                        grantedBy = session.playerId,
                    ),
                ),
            ),
        )
        return responseWithSubscribedChannelsRefresh(
            commandId = commandId,
            session = session,
        )
    }

    private suspend fun handleJoinChannel(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatChannelJoinPayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }
        validatePassword(payload.password)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_PASSWORD", it) }

        val channel = loadChannel(payload.channelName)
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_NOT_FOUND",
                message = "Retained chat channel `${payload.channelName}` does not exist.",
            )
        val memberships = chatSocialRepository.listMemberships(channel.channelId)
        if (memberships.any { it.playerId == session.playerId }) {
            return responseWithSubscribedChannelsRefresh(
                commandId = commandId,
                session = session,
            )
        }
        val policy = decodeChannelPolicy(channel.channelPayload)
        if (policy.password.isNotBlank() && policy.password != payload.password) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_PASSWORD_INCORRECT",
                message = "Retained chat password is incorrect for `${payload.channelName}`.",
            )
        }
        if (memberships.size >= MAX_CHANNEL_USERS) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_FULL",
                message = "Retained chat channel `${payload.channelName}` is full.",
            )
        }
        val existingMemberships = loadMembershipsForPlayer(session.playerId)
        if (existingMemberships.size >= MAX_CHANNELS_PER_PLAYER) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MAX_CHANNELS_REACHED",
                message = "Retained chat only allows $MAX_CHANNELS_PER_PLAYER subscribed channels.",
            )
        }

        val role = if (memberships.isEmpty()) PersistedChannelRole.OWNER else PersistedChannelRole.MEMBER
        if (role == PersistedChannelRole.OWNER && channel.ownerPlayerId != session.playerId) {
            chatSocialRepository.upsertChannel(channel.copy(ownerPlayerId = session.playerId))
        }
        chatSocialRepository.upsertMembership(
            PersistedChatChannelMembership(
                channelId = channel.channelId,
                playerId = session.playerId,
                role = role,
                joinedAt = clock(),
                membershipPayload = encodeMembershipPayload(
                    MembershipPayload(
                        source = "join",
                        grantedBy = if (role == PersistedChannelRole.OWNER) session.playerId else channel.ownerPlayerId,
                    ),
                ),
            ),
        )
        return responseWithSubscribedChannelsRefresh(
            commandId = commandId,
            session = session,
        )
    }

    private suspend fun handleLeaveChannel(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatChannelLeavePayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }

        val channel = loadChannel(payload.channelName)
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_NOT_FOUND",
                message = "Retained chat channel `${payload.channelName}` does not exist.",
            )
        val memberships = chatSocialRepository.listMemberships(channel.channelId)
        val actorMembership = memberships.firstOrNull { it.playerId == session.playerId }
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_NOT_SUBSCRIBED",
                message = "Retained chat player `${session.playerId}` is not subscribed to `${payload.channelName}`.",
            )

        removeChannelMembership(
            channel = channel,
            memberships = memberships,
            removedMembership = actorMembership,
        )
        return responseWithSubscribedChannelsRefresh(
            commandId = commandId,
            session = session,
        )
    }

    private suspend fun handleKickChannelMember(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatChannelKickPayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }

        val channel = loadChannel(payload.channelName)
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_NOT_FOUND",
                message = "Retained chat channel `${payload.channelName}` does not exist.",
            )
        val memberships = chatSocialRepository.listMemberships(channel.channelId)
        val actorMembership = memberships.firstOrNull { it.playerId == session.playerId }
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_NOT_SUBSCRIBED",
                message = "Retained chat player `${session.playerId}` is not subscribed to `${payload.channelName}`.",
            )
        val targetMembership = memberships.firstOrNull { it.playerId == payload.targetPlayerId }
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_KICK_TARGET_MISSING",
                message = "Retained chat kick target `${payload.targetPlayerId}` is not subscribed to `${payload.channelName}`.",
            )
        if (payload.targetPlayerId == session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_KICK_SELF_FORBIDDEN",
                message = "Retained chat kick cannot target the acting player.",
            )
        }
        val policy = decodeChannelPolicy(channel.channelPayload)
        if (!canKick(actorMembership, targetMembership, channel, policy)) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_KICK_FORBIDDEN",
                message = "Retained chat kick is not allowed for `${payload.channelName}`.",
            )
        }

        removeChannelMembership(
            channel = channel,
            memberships = memberships,
            removedMembership = targetMembership,
        )
        return responseWithSubscribedChannelsRefresh(
            commandId = commandId,
            session = session,
        )
    }

    private suspend fun handleAddChannelModerator(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatAddAdminPayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }

        val channel = loadChannel(payload.channelName)
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_NOT_FOUND",
                message = "Retained chat channel `${payload.channelName}` does not exist.",
            )
        val memberships = chatSocialRepository.listMemberships(channel.channelId)
        val actorMembership = memberships.firstOrNull { it.playerId == session.playerId }
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_NOT_SUBSCRIBED",
                message = "Retained chat player `${session.playerId}` is not subscribed to `${payload.channelName}`.",
            )
        if (actorMembership.role != PersistedChannelRole.OWNER) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MODERATOR_GRANT_FORBIDDEN",
                message = "Retained chat moderator grants require channel ownership for `${payload.channelName}`.",
            )
        }
        if (payload.receiverPlayerId == session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MODERATOR_GRANT_SELF_FORBIDDEN",
                message = "Retained chat moderator grants cannot target the acting player.",
            )
        }
        val targetMembership = memberships.firstOrNull { it.playerId == payload.receiverPlayerId }
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MODERATOR_TARGET_MISSING",
                message = "Retained chat moderator target `${payload.receiverPlayerId}` is not subscribed to `${payload.channelName}`.",
            )
        if (targetMembership.role == PersistedChannelRole.MEMBER) {
            chatSocialRepository.upsertMembership(targetMembership.copy(role = PersistedChannelRole.MODERATOR))
        }
        return responseWithSubscribedChannelsRefresh(
            commandId = commandId,
            session = session,
        )
    }

    private suspend fun handleMuteChannelMember(
        commandId: String,
        session: AuthenticatedChatSession,
        payload: ChatMutePayload,
    ): List<FrameEnvelope> {
        if (payload.senderPlayerId != session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_SENDER_MISMATCH",
                message = "Retained chat sender identity must match the authenticated player.",
            )
        }
        validateChannelName(payload.channelName)?.let { return requestErrorFrames(commandId, session.playerId, "CHAT_INVALID_CHANNEL_NAME", it) }

        val channel = loadChannel(payload.channelName)
            ?: return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_CHANNEL_NOT_FOUND",
                message = "Retained chat channel `${payload.channelName}` does not exist.",
            )
        val memberships = chatSocialRepository.listMemberships(channel.channelId)
        if (memberships.none { it.playerId == session.playerId }) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_NOT_SUBSCRIBED",
                message = "Retained chat player `${session.playerId}` is not subscribed to `${payload.channelName}`.",
            )
        }
        if (payload.receiverPlayerId == session.playerId) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MUTE_SELF_FORBIDDEN",
                message = "Retained chat mute cannot target the acting player.",
            )
        }
        if (memberships.none { it.playerId == payload.receiverPlayerId }) {
            return requestErrorFrames(
                commandId = commandId,
                receiverPlayerId = session.playerId,
                code = "CHAT_MUTE_TARGET_MISSING",
                message = "Retained chat mute target `${payload.receiverPlayerId}` is not subscribed to `${payload.channelName}`.",
            )
        }
        chatSocialRepository.upsertChannelMute(
            PersistedChannelMute(
                playerId = session.playerId,
                channelId = channel.channelId,
                mutedPlayerId = payload.receiverPlayerId,
                createdAt = clock(),
                mutePayload = """{"source":"channel_mute"}""",
            ),
        )
        return listOf(RewriteFrames.commandResponse(commandId = commandId))
    }

    private suspend fun removeChannelMembership(
        channel: PersistedChatChannel,
        memberships: List<PersistedChatChannelMembership>,
        removedMembership: PersistedChatChannelMembership,
    ) {
        chatSocialRepository.deleteMembership(
            channelId = removedMembership.channelId,
            playerId = removedMembership.playerId,
        )
        val remainingMemberships = memberships
            .filterNot { it.channelId == removedMembership.channelId && it.playerId == removedMembership.playerId }
            .sortedBy { it.joinedAt }
        if (remainingMemberships.isEmpty()) {
            val policy = decodeChannelPolicy(channel.channelPayload)
            if (policy.removeWhenEmpty) {
                chatSocialRepository.deleteChannel(channel.channelId)
            }
            return
        }
        if (channel.ownerPlayerId == removedMembership.playerId || removedMembership.role == PersistedChannelRole.OWNER) {
            val successor = remainingMemberships.first()
            if (successor.role != PersistedChannelRole.OWNER) {
                chatSocialRepository.upsertMembership(successor.copy(role = PersistedChannelRole.OWNER))
            }
            if (channel.ownerPlayerId != successor.playerId) {
                chatSocialRepository.upsertChannel(channel.copy(ownerPlayerId = successor.playerId))
            }
        }
    }

    private suspend fun responseWithSubscribedChannelsRefresh(
        commandId: String,
        session: AuthenticatedChatSession,
    ): List<FrameEnvelope> {
        return listOf(
            RewriteFrames.commandResponse(commandId = commandId),
            RewriteFrames.chatEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = ChatParityEventType.SUB_CHANNELS,
                payload = RewriteChatJson.encode(
                    serializer = com.hackwars.rewrite.protocol.ChatSubChannelsEventPayload.serializer(),
                    value = RetainedChatBootstrapPolicy.projectSubscribedChannels(
                        receiverPlayerId = PlayerId(session.playerId),
                        channelRosters = loadSubscribedChannelRosters(session.playerId),
                    ),
                ),
            ),
        )
    }

    private suspend fun loadMembershipsForPlayer(playerId: String): List<PersistedChatChannelMembership> {
        return buildList {
            for (channel in chatSocialRepository.listChannels()) {
                addAll(
                    chatSocialRepository.listMemberships(channel.channelId)
                        .filter { it.playerId == playerId },
                )
            }
        }
    }

    private suspend fun loadChannel(channelId: String): PersistedChatChannel? {
        return chatSocialRepository.listChannels().firstOrNull { it.channelId == channelId }
    }

    private fun canKick(
        actorMembership: PersistedChatChannelMembership,
        targetMembership: PersistedChatChannelMembership,
        channel: PersistedChatChannel,
        policy: ChannelPolicyPayload,
    ): Boolean {
        if (!policy.adminCanKick) {
            return false
        }
        if (actorMembership.role == PersistedChannelRole.MEMBER) {
            return false
        }
        if (targetMembership.playerId == channel.ownerPlayerId || targetMembership.role == PersistedChannelRole.OWNER) {
            return false
        }
        return true
    }

    private fun validateChannelName(channelName: String): String? {
        return when {
            channelName.isBlank() -> "Retained chat channel names must not be blank."
            channelName.length > MAX_CHANNEL_NAME_LENGTH -> "Retained chat channel names must be at most $MAX_CHANNEL_NAME_LENGTH characters."
            !VALID_CHANNEL_NAME.matches(channelName) -> {
                "Retained chat channel names may only use letters, numbers, spaces, dots, underscores, dashes, and angle brackets."
            }

            else -> null
        }
    }

    private fun validatePassword(password: String): String? {
        return if (password.length > MAX_CHANNEL_PASSWORD_LENGTH) {
            "Retained chat passwords must be at most $MAX_CHANNEL_PASSWORD_LENGTH characters."
        } else {
            null
        }
    }

    private fun decodeChannelPolicy(payload: String): ChannelPolicyPayload {
        return runCatching {
            RewriteChatJson.codec.decodeFromString(ChannelPolicyPayload.serializer(), payload)
        }.getOrDefault(ChannelPolicyPayload())
    }

    private fun encodeChannelPolicy(policy: ChannelPolicyPayload): String {
        return RewriteChatJson.codec.encodeToString(ChannelPolicyPayload.serializer(), policy)
    }

    private fun encodeMembershipPayload(payload: MembershipPayload): String {
        return RewriteChatJson.codec.encodeToString(MembershipPayload.serializer(), payload)
    }

    private suspend fun <T> decodeAndHandle(
        command: CommandEnvelope,
        session: AuthenticatedChatSession,
        serializer: kotlinx.serialization.KSerializer<T>,
        handler: suspend (T) -> List<FrameEnvelope>,
    ): List<FrameEnvelope> {
        val payload = runCatching {
            RewriteChatJson.decode(serializer = serializer, bytes = command.payload.toByteArray())
        }.getOrElse {
            return requestErrorFrames(
                    commandId = command.command_id,
                    receiverPlayerId = session.playerId,
                    code = "CHAT_PAYLOAD_INVALID",
                    message = "Retained chat could not decode `${command.command_name}` payload.",
                )
        }
        return handler(payload)
    }

    private fun requestErrorFrames(
        commandId: String,
        receiverPlayerId: String,
        code: String,
        message: String,
    ): List<FrameEnvelope> {
        return listOf(
            requestErrorResponse(commandId, receiverPlayerId, code, message),
            errorEvent(
                receiverPlayerId = receiverPlayerId,
                message = message,
            ),
        )
    }

    private fun requestErrorResponse(
        commandId: String,
        receiverPlayerId: String,
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

    private fun errorEvent(
        receiverPlayerId: String,
        message: String,
    ): FrameEnvelope {
        return RewriteFrames.chatEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = ChatParityEventType.ERROR,
            payload = RewriteChatJson.encode(
                serializer = ChatErrorEventPayload.serializer(),
                value = ChatErrorEventPayload(
                    receiverPlayerId = receiverPlayerId,
                    message = message,
                ),
            ),
        )
    }

    private suspend fun loadSubscribedChannelRosters(playerId: String): List<ChatChannelRoster> {
        return chatSocialRepository.listChannels()
            .sortedWith(
                compareBy<PersistedChatChannel>(
                    { RetainedChatBootstrapPolicy.defaultAutoChannels.indexOf(it.channelId).let { index -> if (index >= 0) index else Int.MAX_VALUE } },
                    { it.createdAt },
                    { it.channelId },
                ),
            )
            .mapNotNull { channel ->
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

    fun bindTransport(
        pushFrame: suspend (connectionId: String, frame: FrameEnvelope) -> Unit,
    ) {
        this.pushFrame = pushFrame
    }

    suspend fun pushToPlayerIds(
        playerIds: Set<String>,
        frames: List<FrameEnvelope>,
    ) {
        val recipientConnectionIds = activeSessions.values
            .filter { it.playerId in playerIds }
            .map { it.connectionId }
        for (connectionId in recipientConnectionIds) {
            for (frame in frames) {
                pushFrame(connectionId, frame)
            }
        }
    }

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
        const val MAX_CHANNELS_PER_PLAYER: Int = 6
        const val MAX_CHANNEL_USERS: Int = 120
        const val MAX_CHANNEL_NAME_LENGTH: Int = 18
        const val MAX_CHANNEL_PASSWORD_LENGTH: Int = 18
        val VALID_CHANNEL_NAME: Regex = Regex("^[A-Za-z0-9 ._<>-]{1,18}$")

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

    override fun bindTransport(
        pushFrame: suspend (connectionId: String, frame: FrameEnvelope) -> Unit,
    ) {
        adapter.bindTransport(pushFrame)
    }

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

@Serializable
private data class ChannelPolicyPayload(
    val kind: String = "user",
    val password: String = "",
    val adminCanKick: Boolean = true,
    val removeWhenEmpty: Boolean = true,
)

@Serializable
private data class MembershipPayload(
    val source: String = "",
    val grantedBy: String = "",
)
