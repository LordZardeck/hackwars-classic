package com.hackwars.rewrite.persistence

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class JdbcAuthSessionRepository(
    private val connectionFactory: () -> Connection,
) : AuthSessionRepository {
    override suspend fun upsertSessionTicket(ticket: PersistedSessionTicket) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_session_ticket(
                    session_ticket,
                    player_id,
                    playfab_id,
                    player_ip,
                    issued_at,
                    expires_at,
                    ticket_payload
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (session_ticket) do update
                set player_id = excluded.player_id,
                    playfab_id = excluded.playfab_id,
                    player_ip = excluded.player_ip,
                    issued_at = excluded.issued_at,
                    expires_at = excluded.expires_at,
                    ticket_payload = excluded.ticket_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, ticket.sessionTicket)
                statement.setString(2, ticket.playerId)
                statement.setString(3, ticket.playFabId)
                statement.setString(4, ticket.playerIp)
                statement.setTimestamp(5, Timestamp.from(ticket.issuedAt))
                statement.setTimestamp(6, ticket.expiresAt?.let(Timestamp::from))
                statement.setString(7, ticket.ticketPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findSessionTicket(sessionTicket: String): PersistedSessionTicket? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    session_ticket,
                    player_id,
                    playfab_id,
                    player_ip,
                    issued_at,
                    expires_at,
                    ticket_payload::text as ticket_payload
                from rewrite_session_ticket
                where session_ticket = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sessionTicket)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return mapSessionTicket(resultSet)
                }
            }
        }
    }

    override suspend fun upsertServiceSession(session: PersistedServiceSession) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_service_session(
                    service_session_id,
                    service_kind,
                    connection_id,
                    player_id,
                    playfab_id,
                    player_ip,
                    session_ticket,
                    client_build,
                    heartbeat_interval_millis,
                    authenticated_at,
                    last_seen_at,
                    closed_at,
                    session_payload
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (service_kind, connection_id) do update
                set service_session_id = excluded.service_session_id,
                    player_id = excluded.player_id,
                    playfab_id = excluded.playfab_id,
                    player_ip = excluded.player_ip,
                    session_ticket = excluded.session_ticket,
                    client_build = excluded.client_build,
                    heartbeat_interval_millis = excluded.heartbeat_interval_millis,
                    authenticated_at = excluded.authenticated_at,
                    last_seen_at = excluded.last_seen_at,
                    closed_at = excluded.closed_at,
                    session_payload = excluded.session_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, session.serviceSessionId)
                statement.setString(2, session.serviceKind.name)
                statement.setString(3, session.connectionId)
                statement.setString(4, session.playerId)
                statement.setString(5, session.playFabId)
                statement.setString(6, session.playerIp)
                statement.setString(7, session.sessionTicket)
                statement.setString(8, session.clientBuild)
                statement.setLong(9, session.heartbeatIntervalMillis)
                statement.setTimestamp(10, Timestamp.from(session.authenticatedAt))
                statement.setTimestamp(11, Timestamp.from(session.lastSeenAt))
                statement.setTimestamp(12, session.closedAt?.let(Timestamp::from))
                statement.setString(13, session.sessionPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
    ): PersistedServiceSession? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    service_session_id,
                    service_kind,
                    connection_id,
                    player_id,
                    playfab_id,
                    player_ip,
                    session_ticket,
                    client_build,
                    heartbeat_interval_millis,
                    authenticated_at,
                    last_seen_at,
                    closed_at,
                    session_payload::text as session_payload
                from rewrite_service_session
                where service_kind = ?
                  and connection_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, serviceKind.name)
                statement.setString(2, connectionId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return mapServiceSession(resultSet)
                }
            }
        }
    }

    override suspend fun listActiveServiceSessions(playerId: String): List<PersistedServiceSession> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    service_session_id,
                    service_kind,
                    connection_id,
                    player_id,
                    playfab_id,
                    player_ip,
                    session_ticket,
                    client_build,
                    heartbeat_interval_millis,
                    authenticated_at,
                    last_seen_at,
                    closed_at,
                    session_payload::text as session_payload
                from rewrite_service_session
                where player_id = ?
                  and closed_at is null
                order by authenticated_at asc, service_kind asc, connection_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.executeQuery().use { resultSet ->
                    val sessions = mutableListOf<PersistedServiceSession>()
                    while (resultSet.next()) {
                        sessions += mapServiceSession(resultSet)
                    }
                    return sessions
                }
            }
        }
    }

    override suspend fun touchServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        lastSeenAt: Instant,
    ) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                update rewrite_service_session
                set last_seen_at = ?
                where service_kind = ?
                  and connection_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(lastSeenAt))
                statement.setString(2, serviceKind.name)
                statement.setString(3, connectionId)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun closeServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        closedAt: Instant,
    ) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                update rewrite_service_session
                set closed_at = ?,
                    last_seen_at = greatest(last_seen_at, ?)
                where service_kind = ?
                  and connection_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(closedAt))
                statement.setTimestamp(2, Timestamp.from(closedAt))
                statement.setString(3, serviceKind.name)
                statement.setString(4, connectionId)
                statement.executeUpdate()
            }
        }
    }

    private fun mapSessionTicket(resultSet: java.sql.ResultSet): PersistedSessionTicket {
        return PersistedSessionTicket(
            sessionTicket = resultSet.getString("session_ticket"),
            playerId = resultSet.getString("player_id"),
            playFabId = resultSet.getString("playfab_id"),
            playerIp = resultSet.getString("player_ip"),
            issuedAt = resultSet.getTimestamp("issued_at").toInstant(),
            expiresAt = resultSet.getTimestamp("expires_at")?.toInstant(),
            ticketPayload = resultSet.getString("ticket_payload"),
        )
    }

    private fun mapServiceSession(resultSet: java.sql.ResultSet): PersistedServiceSession {
        return PersistedServiceSession(
            serviceSessionId = resultSet.getString("service_session_id"),
            serviceKind = PersistedServiceKind.valueOf(resultSet.getString("service_kind")),
            connectionId = resultSet.getString("connection_id"),
            playerId = resultSet.getString("player_id"),
            playFabId = resultSet.getString("playfab_id"),
            playerIp = resultSet.getString("player_ip"),
            sessionTicket = resultSet.getString("session_ticket"),
            clientBuild = resultSet.getString("client_build"),
            heartbeatIntervalMillis = resultSet.getLong("heartbeat_interval_millis"),
            authenticatedAt = resultSet.getTimestamp("authenticated_at").toInstant(),
            lastSeenAt = resultSet.getTimestamp("last_seen_at").toInstant(),
            closedAt = resultSet.getTimestamp("closed_at")?.toInstant(),
            sessionPayload = resultSet.getString("session_payload"),
        )
    }
}
