package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JdbcImportAuditRepository(
    private val connectionFactory: () -> Connection,
) : ImportAuditRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listImportBatches(): List<PersistedImportBatchSummary> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    batch_id,
                    source_kind,
                    source_location,
                    seed_payload::text as seed_payload,
                    created_at
                from rewrite_import_batch
                order by created_at asc, batch_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val batches = mutableListOf<PersistedImportBatchSummary>()
                    while (resultSet.next()) {
                        val payload = parsePayload(resultSet.getString("seed_payload"))
                        batches += PersistedImportBatchSummary(
                            batchId = resultSet.getString("batch_id"),
                            sourceKind = resultSet.getString("source_kind"),
                            sourceLocation = resultSet.getString("source_location"),
                            payloadType = payload.string("type"),
                            createdAt = resultSet.getTimestamp("created_at").toInstant(),
                        )
                    }
                    return batches
                }
            }
        }
    }

    override suspend fun buildRetainedReconciliationReport(): RetainedImportReconciliationReport {
        return connectionFactory().use { connection ->
            val batches = loadPayloads(connection)
            RetainedImportReconciliationReport(
                batchCount = batches.size,
                payloadCountsByType = batches.groupingBy { it.string("type") }.eachCount().toSortedMap(),
                missingPlayerIds = batches
                    .filterType("player")
                    .mapNotNull { payload ->
                        payload.stringOrNull("playerId")?.takeUnless { playerExists(connection, it) }
                    },
                missingSessionTickets = batches
                    .filterType("session-ticket")
                    .mapNotNull { payload ->
                        payload.stringOrNull("sessionTicket")?.takeUnless { sessionTicketExists(connection, it) }
                    },
                missingServiceSessions = batches
                    .filterType("service-session")
                    .mapNotNull { payload ->
                        val serviceKind = payload.stringOrNull("serviceKind")
                        val connectionId = payload.stringOrNull("connectionId")
                        if (serviceKind == null || connectionId == null || serviceSessionExists(connection, serviceKind, connectionId)) {
                            null
                        } else {
                            "$serviceKind:$connectionId"
                        }
                    },
                missingComputerIds = batches
                    .filterType("computer")
                    .mapNotNull { payload ->
                        payload.stringOrNull("computerId")?.takeUnless { computerExists(connection, it) }
                    },
                missingNetworkNames = batches
                    .filterType("world")
                    .flatMap { payload ->
                        payload.array("networks").mapNotNull { network ->
                            network.jsonObject.stringOrNull("name")?.takeUnless { networkExists(connection, it) }
                        }
                    },
                missingWebsiteStateIds = batches
                    .filterType("inventory")
                    .mapNotNull { payload ->
                        payload.stringOrNull("computerId")?.takeUnless { websiteProjectionExists(connection, it) }
                    },
                missingChatChannelIds = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("channels").mapNotNull { channel ->
                            channel.jsonObject.stringOrNull("channelId")?.takeUnless { chatChannelExists(connection, it) }
                        }
                    },
                missingChatMemberships = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("memberships").mapNotNull { membership ->
                            val channelId = membership.jsonObject.stringOrNull("channelId")
                            val playerId = membership.jsonObject.stringOrNull("playerId")
                            if (channelId == null || playerId == null || chatMembershipExists(connection, channelId, playerId)) {
                                null
                            } else {
                                "$channelId:$playerId"
                            }
                        }
                    },
                missingChatMessageIds = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("messages").mapNotNull { message ->
                            message.jsonObject.stringOrNull("messageId")?.takeUnless { chatMessageExists(connection, it) }
                        }
                    },
                missingChatRelations = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("relations").mapNotNull { relation ->
                            val playerId = relation.jsonObject.stringOrNull("playerId")
                            val targetPlayerId = relation.jsonObject.stringOrNull("targetPlayerId")
                            val relationKind = relation.jsonObject.stringOrNull("relationKind")
                            if (
                                playerId == null ||
                                targetPlayerId == null ||
                                relationKind == null ||
                                chatRelationExists(connection, playerId, targetPlayerId, relationKind)
                            ) {
                                null
                            } else {
                                "$playerId:$targetPlayerId:$relationKind"
                            }
                        }
                    },
                missingChatMutes = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("channelMutes").mapNotNull { mute ->
                            val playerId = mute.jsonObject.stringOrNull("playerId")
                            val channelId = mute.jsonObject.stringOrNull("channelId")
                            val mutedPlayerId = mute.jsonObject.stringOrNull("mutedPlayerId")
                            if (
                                playerId == null ||
                                channelId == null ||
                                mutedPlayerId == null ||
                                chatMuteExists(connection, playerId, channelId, mutedPlayerId)
                            ) {
                                null
                            } else {
                                "$playerId:$channelId:$mutedPlayerId"
                            }
                        }
                    },
                missingChatPresenceConnectionIds = batches
                    .filterType("chat-social")
                    .flatMap { payload ->
                        payload.array("presence").mapNotNull { presence ->
                            presence.jsonObject.stringOrNull("connectionId")?.takeUnless {
                                chatPresenceExists(connection, it)
                            }
                        }
                    },
            )
        }
    }

    private fun loadPayloads(connection: Connection): List<JsonObject> {
        connection.prepareStatement(
            """
            select seed_payload::text as seed_payload
            from rewrite_import_batch
            order by created_at asc, batch_id asc
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                val payloads = mutableListOf<JsonObject>()
                while (resultSet.next()) {
                    payloads += parsePayload(resultSet.getString("seed_payload"))
                }
                return payloads
            }
        }
    }

    private fun parsePayload(raw: String): JsonObject {
        return json.parseToJsonElement(raw).jsonObject
    }

    private fun playerExists(connection: Connection, playerId: String): Boolean {
        return exists(connection, "select 1 from rewrite_player_account where player_id = ?", playerId)
    }

    private fun sessionTicketExists(connection: Connection, sessionTicket: String): Boolean {
        return exists(connection, "select 1 from rewrite_session_ticket where session_ticket = ?", sessionTicket)
    }

    private fun serviceSessionExists(
        connection: Connection,
        serviceKind: String,
        connectionId: String,
    ): Boolean {
        return connection.prepareStatement(
            """
            select 1
            from rewrite_service_session
            where service_kind = ?
              and connection_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, serviceKind)
            statement.setString(2, connectionId)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun computerExists(connection: Connection, computerId: String): Boolean {
        return exists(connection, "select 1 from rewrite_computer_state where computer_id = ?", computerId)
    }

    private fun networkExists(connection: Connection, networkName: String): Boolean {
        return exists(connection, "select 1 from rewrite_network_directory where network_name = ?", networkName)
    }

    private fun websiteProjectionExists(connection: Connection, stateId: String): Boolean {
        return exists(connection, "select 1 from rewrite_website_projection where state_id = ?", stateId)
    }

    private fun chatChannelExists(connection: Connection, channelId: String): Boolean {
        return exists(connection, "select 1 from rewrite_chat_channel where channel_id = ?", channelId)
    }

    private fun chatMembershipExists(
        connection: Connection,
        channelId: String,
        playerId: String,
    ): Boolean {
        return connection.prepareStatement(
            """
            select 1
            from rewrite_chat_channel_member
            where channel_id = ?
              and player_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, channelId)
            statement.setString(2, playerId)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun chatMessageExists(connection: Connection, messageId: String): Boolean {
        return exists(connection, "select 1 from rewrite_chat_message where message_id = ?", messageId)
    }

    private fun chatRelationExists(
        connection: Connection,
        playerId: String,
        targetPlayerId: String,
        relationKind: String,
    ): Boolean {
        return connection.prepareStatement(
            """
            select 1
            from rewrite_chat_relation
            where player_id = ?
              and target_player_id = ?
              and relation_kind = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playerId)
            statement.setString(2, targetPlayerId)
            statement.setString(3, relationKind)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun chatMuteExists(
        connection: Connection,
        playerId: String,
        channelId: String,
        mutedPlayerId: String,
    ): Boolean {
        return connection.prepareStatement(
            """
            select 1
            from rewrite_chat_channel_mute
            where player_id = ?
              and channel_id = ?
              and muted_player_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playerId)
            statement.setString(2, channelId)
            statement.setString(3, mutedPlayerId)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun chatPresenceExists(connection: Connection, connectionId: String): Boolean {
        return exists(connection, "select 1 from rewrite_chat_presence where connection_id = ?", connectionId)
    }

    private fun exists(
        connection: Connection,
        sql: String,
        value: String,
    ): Boolean {
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun List<JsonObject>.filterType(type: String): List<JsonObject> {
        return filter { it.string("type") == type }
    }

    private fun JsonObject.array(key: String): List<JsonElement> {
        return (this[key] as? JsonArray)?.toList() ?: emptyList()
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }
}
