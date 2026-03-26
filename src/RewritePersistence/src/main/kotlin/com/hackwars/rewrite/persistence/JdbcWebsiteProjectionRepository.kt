package com.hackwars.rewrite.persistence

import java.sql.Connection

class JdbcWebsiteProjectionRepository(
    private val connectionFactory: () -> Connection,
) : WebsiteProjectionRepository {
    override suspend fun upsertWebsiteProjection(projection: PersistedWebsiteProjection) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_website_projection(
                    state_id,
                    canonical_address,
                    title,
                    body_html,
                    vote_count,
                    votes_available,
                    store_revenue_target_state_id,
                    website_payload
                )
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (state_id) do update
                set canonical_address = excluded.canonical_address,
                    title = excluded.title,
                    body_html = excluded.body_html,
                    vote_count = excluded.vote_count,
                    votes_available = excluded.votes_available,
                    store_revenue_target_state_id = excluded.store_revenue_target_state_id,
                    website_payload = excluded.website_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, projection.stateId)
                statement.setString(2, projection.canonicalAddress)
                statement.setString(3, projection.title)
                statement.setString(4, projection.bodyHtml)
                statement.setInt(5, projection.voteCount)
                statement.setInt(6, projection.votesAvailable)
                statement.setString(7, projection.storeRevenueTargetStateId)
                statement.setString(8, projection.websitePayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findWebsiteProjection(stateId: String): PersistedWebsiteProjection? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    state_id,
                    canonical_address,
                    title,
                    body_html,
                    vote_count,
                    votes_available,
                    store_revenue_target_state_id,
                    website_payload::text as website_payload
                from rewrite_website_projection
                where state_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, stateId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return mapProjection(resultSet)
                }
            }
        }
    }

    override suspend fun listWebsiteProjections(): List<PersistedWebsiteProjection> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    state_id,
                    canonical_address,
                    title,
                    body_html,
                    vote_count,
                    votes_available,
                    store_revenue_target_state_id,
                    website_payload::text as website_payload
                from rewrite_website_projection
                order by canonical_address asc, state_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val projections = mutableListOf<PersistedWebsiteProjection>()
                    while (resultSet.next()) {
                        projections += mapProjection(resultSet)
                    }
                    return projections
                }
            }
        }
    }

    private fun mapProjection(resultSet: java.sql.ResultSet): PersistedWebsiteProjection {
        return PersistedWebsiteProjection(
            stateId = resultSet.getString("state_id"),
            canonicalAddress = resultSet.getString("canonical_address"),
            title = resultSet.getString("title"),
            bodyHtml = resultSet.getString("body_html"),
            voteCount = resultSet.getInt("vote_count"),
            votesAvailable = resultSet.getInt("votes_available"),
            storeRevenueTargetStateId = resultSet.getString("store_revenue_target_state_id"),
            websitePayload = resultSet.getString("website_payload"),
        )
    }
}
