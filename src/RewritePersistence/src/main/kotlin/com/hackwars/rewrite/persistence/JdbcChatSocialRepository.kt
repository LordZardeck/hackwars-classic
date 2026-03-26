package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class JdbcChatSocialRepository(
    private val connectionFactory: () -> Connection,
) : ChatSocialRepository {
    override suspend fun upsertChannel(channel: PersistedChatChannel) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel(
                    channel_id,
                    display_name,
                    topic,
                    owner_player_id,
                    private_channel,
                    created_at,
                    channel_payload
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (channel_id) do update
                set display_name = excluded.display_name,
                    topic = excluded.topic,
                    owner_player_id = excluded.owner_player_id,
                    private_channel = excluded.private_channel,
                    created_at = excluded.created_at,
                    channel_payload = excluded.channel_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, channel.channelId)
                statement.setString(2, channel.displayName)
                statement.setString(3, channel.topic)
                statement.setString(4, channel.ownerPlayerId)
                statement.setBoolean(5, channel.privateChannel)
                statement.setTimestamp(6, Timestamp.from(channel.createdAt))
                statement.setString(7, channel.channelPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun listChannels(): List<PersistedChatChannel> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    channel_id,
                    display_name,
                    topic,
                    owner_player_id,
                    private_channel,
                    created_at,
                    channel_payload::text as channel_payload
                from rewrite_chat_channel
                order by created_at asc, channel_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val channels = mutableListOf<PersistedChatChannel>()
                    while (resultSet.next()) {
                        channels += mapChannel(resultSet)
                    }
                    return channels
                }
            }
        }
    }

    override suspend fun deleteChannel(channelId: String) {
        connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                deleteChannelDependents(connection, channelId)
                connection.prepareStatement(
                    """
                    delete from rewrite_chat_channel
                    where channel_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, channelId)
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun upsertMembership(membership: PersistedChatChannelMembership) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel_member(
                    channel_id,
                    player_id,
                    role,
                    joined_at,
                    membership_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (channel_id, player_id) do update
                set role = excluded.role,
                    joined_at = excluded.joined_at,
                    membership_payload = excluded.membership_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, membership.channelId)
                statement.setString(2, membership.playerId)
                statement.setString(3, membership.role.name)
                statement.setTimestamp(4, Timestamp.from(membership.joinedAt))
                statement.setString(5, membership.membershipPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun listMemberships(channelId: String): List<PersistedChatChannelMembership> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    channel_id,
                    player_id,
                    role,
                    joined_at,
                    membership_payload::text as membership_payload
                from rewrite_chat_channel_member
                where channel_id = ?
                order by joined_at asc, player_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, channelId)
                statement.executeQuery().use { resultSet ->
                    val memberships = mutableListOf<PersistedChatChannelMembership>()
                    while (resultSet.next()) {
                        memberships += mapMembership(resultSet)
                    }
                    return memberships
                }
            }
        }
    }

    override suspend fun deleteMembership(
        channelId: String,
        playerId: String,
    ) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                delete from rewrite_chat_channel_member
                where channel_id = ?
                  and player_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, channelId)
                statement.setString(2, playerId)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun appendMessage(message: PersistedChatMessage) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_message(
                    message_id,
                    message_kind,
                    channel_id,
                    sender_player_id,
                    recipient_player_id,
                    event_type,
                    payload,
                    created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, message.messageId)
                statement.setString(2, message.messageKind.name)
                statement.setString(3, message.channelId)
                statement.setString(4, message.senderPlayerId)
                statement.setString(5, message.recipientPlayerId)
                statement.setString(6, message.eventType)
                statement.setBytes(7, message.payload)
                statement.setTimestamp(8, Timestamp.from(message.createdAt))
                statement.executeUpdate()
            }
        }
    }

    override suspend fun loadChannelHistory(
        channelId: String,
        limit: Int,
    ): List<PersistedChatMessage> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    message_id,
                    message_kind,
                    channel_id,
                    sender_player_id,
                    recipient_player_id,
                    event_type,
                    payload,
                    created_at
                from rewrite_chat_message
                where channel_id = ?
                order by created_at asc, message_id asc
                limit ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, channelId)
                statement.setInt(2, limit)
                statement.executeQuery().use { resultSet ->
                    val messages = mutableListOf<PersistedChatMessage>()
                    while (resultSet.next()) {
                        messages += mapMessage(resultSet)
                    }
                    return messages
                }
            }
        }
    }

    override suspend fun upsertRelation(relation: PersistedChatRelation) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_relation(
                    player_id,
                    target_player_id,
                    relation_kind,
                    created_at,
                    relation_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (player_id, target_player_id, relation_kind) do update
                set created_at = excluded.created_at,
                    relation_payload = excluded.relation_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, relation.playerId)
                statement.setString(2, relation.targetPlayerId)
                statement.setString(3, relation.relationKind.name)
                statement.setTimestamp(4, Timestamp.from(relation.createdAt))
                statement.setString(5, relation.relationPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun deleteRelation(
        playerId: String,
        targetPlayerId: String,
        relationKind: PersistedRelationKind,
    ) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                delete from rewrite_chat_relation
                where player_id = ?
                  and target_player_id = ?
                  and relation_kind = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, targetPlayerId)
                statement.setString(3, relationKind.name)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun listRelations(
        playerId: String,
        relationKind: PersistedRelationKind,
    ): List<PersistedChatRelation> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    player_id,
                    target_player_id,
                    relation_kind,
                    created_at,
                    relation_payload::text as relation_payload
                from rewrite_chat_relation
                where player_id = ?
                  and relation_kind = ?
                order by target_player_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, relationKind.name)
                statement.executeQuery().use { resultSet ->
                    val relations = mutableListOf<PersistedChatRelation>()
                    while (resultSet.next()) {
                        relations += mapRelation(resultSet)
                    }
                    return relations
                }
            }
        }
    }

    override suspend fun upsertChannelMute(mute: PersistedChannelMute) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel_mute(
                    player_id,
                    channel_id,
                    muted_player_id,
                    created_at,
                    mute_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (player_id, channel_id, muted_player_id) do update
                set created_at = excluded.created_at,
                    mute_payload = excluded.mute_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, mute.playerId)
                statement.setString(2, mute.channelId)
                statement.setString(3, mute.mutedPlayerId)
                statement.setTimestamp(4, Timestamp.from(mute.createdAt))
                statement.setString(5, mute.mutePayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun listChannelMutes(
        playerId: String,
        channelId: String,
    ): List<PersistedChannelMute> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    player_id,
                    channel_id,
                    muted_player_id,
                    created_at,
                    mute_payload::text as mute_payload
                from rewrite_chat_channel_mute
                where player_id = ?
                  and channel_id = ?
                order by muted_player_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, channelId)
                statement.executeQuery().use { resultSet ->
                    val mutes = mutableListOf<PersistedChannelMute>()
                    while (resultSet.next()) {
                        mutes += mapMute(resultSet)
                    }
                    return mutes
                }
            }
        }
    }

    override suspend fun upsertPresence(presence: PersistedChatPresence) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_presence(
                    connection_id,
                    player_id,
                    online_at,
                    last_seen_at,
                    offline_at,
                    presence_payload
                )
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (connection_id) do update
                set player_id = excluded.player_id,
                    online_at = excluded.online_at,
                    last_seen_at = excluded.last_seen_at,
                    offline_at = excluded.offline_at,
                    presence_payload = excluded.presence_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, presence.connectionId)
                statement.setString(2, presence.playerId)
                statement.setTimestamp(3, Timestamp.from(presence.onlineAt))
                statement.setTimestamp(4, Timestamp.from(presence.lastSeenAt))
                statement.setTimestamp(5, presence.offlineAt?.let(Timestamp::from))
                statement.setString(6, presence.presencePayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun listActivePresence(playerId: String?): List<PersistedChatPresence> {
        return connectionFactory().use { connection ->
            val sql = buildString {
                append(
                    """
                    select
                        connection_id,
                        player_id,
                        online_at,
                        last_seen_at,
                        offline_at,
                        presence_payload::text as presence_payload
                    from rewrite_chat_presence
                    where offline_at is null
                    """.trimIndent(),
                )
                if (playerId != null) {
                    append("\n  and player_id = ?")
                }
                append("\norder by online_at asc, connection_id asc")
            }
            connection.prepareStatement(sql).use { statement ->
                if (playerId != null) {
                    statement.setString(1, playerId)
                }
                statement.executeQuery().use { resultSet ->
                    val presence = mutableListOf<PersistedChatPresence>()
                    while (resultSet.next()) {
                        presence += mapPresence(resultSet)
                    }
                    return presence
                }
            }
        }
    }

    override suspend fun closePresence(
        connectionId: String,
        offlineAt: Instant,
    ) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                update rewrite_chat_presence
                set offline_at = ?,
                    last_seen_at = greatest(last_seen_at, ?)
                where connection_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(offlineAt))
                statement.setTimestamp(2, Timestamp.from(offlineAt))
                statement.setString(3, connectionId)
                statement.executeUpdate()
            }
        }
    }

    private fun mapChannel(resultSet: java.sql.ResultSet): PersistedChatChannel {
        return PersistedChatChannel(
            channelId = resultSet.getString("channel_id"),
            displayName = resultSet.getString("display_name"),
            topic = resultSet.getString("topic") ?: "",
            ownerPlayerId = resultSet.getString("owner_player_id"),
            privateChannel = resultSet.getBoolean("private_channel"),
            createdAt = resultSet.getTimestamp("created_at").toInstant(),
            channelPayload = resultSet.getString("channel_payload"),
        )
    }

    private fun mapMembership(resultSet: java.sql.ResultSet): PersistedChatChannelMembership {
        return PersistedChatChannelMembership(
            channelId = resultSet.getString("channel_id"),
            playerId = resultSet.getString("player_id"),
            role = PersistedChannelRole.valueOf(resultSet.getString("role")),
            joinedAt = resultSet.getTimestamp("joined_at").toInstant(),
            membershipPayload = resultSet.getString("membership_payload"),
        )
    }

    private fun mapMessage(resultSet: java.sql.ResultSet): PersistedChatMessage {
        return PersistedChatMessage(
            messageId = resultSet.getString("message_id"),
            messageKind = PersistedChatMessageKind.valueOf(resultSet.getString("message_kind")),
            eventType = resultSet.getString("event_type"),
            senderPlayerId = resultSet.getString("sender_player_id"),
            createdAt = resultSet.getTimestamp("created_at").toInstant(),
            payload = resultSet.getBytes("payload"),
            channelId = resultSet.getString("channel_id"),
            recipientPlayerId = resultSet.getString("recipient_player_id"),
        )
    }

    private fun mapRelation(resultSet: java.sql.ResultSet): PersistedChatRelation {
        return PersistedChatRelation(
            playerId = resultSet.getString("player_id"),
            targetPlayerId = resultSet.getString("target_player_id"),
            relationKind = PersistedRelationKind.valueOf(resultSet.getString("relation_kind")),
            createdAt = resultSet.getTimestamp("created_at").toInstant(),
            relationPayload = resultSet.getString("relation_payload"),
        )
    }

    private fun mapMute(resultSet: java.sql.ResultSet): PersistedChannelMute {
        return PersistedChannelMute(
            playerId = resultSet.getString("player_id"),
            channelId = resultSet.getString("channel_id"),
            mutedPlayerId = resultSet.getString("muted_player_id"),
            createdAt = resultSet.getTimestamp("created_at").toInstant(),
            mutePayload = resultSet.getString("mute_payload"),
        )
    }

    private fun mapPresence(resultSet: java.sql.ResultSet): PersistedChatPresence {
        return PersistedChatPresence(
            connectionId = resultSet.getString("connection_id"),
            playerId = resultSet.getString("player_id"),
            onlineAt = resultSet.getTimestamp("online_at").toInstant(),
            lastSeenAt = resultSet.getTimestamp("last_seen_at").toInstant(),
            offlineAt = resultSet.getTimestamp("offline_at")?.toInstant(),
            presencePayload = resultSet.getString("presence_payload"),
        )
    }

    private fun deleteChannelDependents(
        connection: Connection,
        channelId: String,
    ) {
        connection.prepareStatement(
            """
            delete from rewrite_chat_channel_mute
            where channel_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, channelId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            delete from rewrite_chat_message
            where channel_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, channelId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            delete from rewrite_chat_channel_member
            where channel_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, channelId)
            statement.executeUpdate()
        }
    }
}
