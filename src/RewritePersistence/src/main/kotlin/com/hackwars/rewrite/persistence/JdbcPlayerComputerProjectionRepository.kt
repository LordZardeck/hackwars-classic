package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ScriptFamily
import java.sql.Connection
import java.sql.Timestamp

class JdbcPlayerComputerProjectionRepository(
    private val connectionFactory: () -> Connection,
) : PlayerComputerProjectionRepository {
    override suspend fun upsertPlayerProfile(profile: PersistedPlayerProfile) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_player_profile(
                    player_id,
                    display_name,
                    profile_image_path,
                    profile_description,
                    profile_location,
                    profile_payload
                )
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (player_id) do update
                set display_name = excluded.display_name,
                    profile_image_path = excluded.profile_image_path,
                    profile_description = excluded.profile_description,
                    profile_location = excluded.profile_location,
                    profile_payload = excluded.profile_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, profile.playerId)
                statement.setString(2, profile.displayName)
                statement.setString(3, profile.profileImagePath)
                statement.setString(4, profile.profileDescription)
                statement.setString(5, profile.profileLocation)
                statement.setString(6, profile.profilePayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findPlayerProfile(playerId: String): PersistedPlayerProfile? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    player_id,
                    display_name,
                    profile_image_path,
                    profile_description,
                    profile_location,
                    profile_payload::text as profile_payload
                from rewrite_player_profile
                where player_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return mapPlayerProfile(resultSet)
                }
            }
        }
    }

    override suspend fun upsertComputerProjection(projection: PersistedComputerProjection) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_computer_projection(
                    computer_id,
                    player_id,
                    ip_address,
                    is_npc,
                    current_network_name,
                    last_login_at,
                    petty_cash,
                    bank_money,
                    default_bank_port,
                    default_redirect_port,
                    daily_pay_base_amount,
                    daily_pay_reduction_multiplier,
                    daily_pay_revenue_target_state_id,
                    daily_pay_last_paid_at_epoch_millis,
                    total_level,
                    noob_protection_level,
                    projection_payload
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (computer_id) do update
                set player_id = excluded.player_id,
                    ip_address = excluded.ip_address,
                    is_npc = excluded.is_npc,
                    current_network_name = excluded.current_network_name,
                    last_login_at = excluded.last_login_at,
                    petty_cash = excluded.petty_cash,
                    bank_money = excluded.bank_money,
                    default_bank_port = excluded.default_bank_port,
                    default_redirect_port = excluded.default_redirect_port,
                    daily_pay_base_amount = excluded.daily_pay_base_amount,
                    daily_pay_reduction_multiplier = excluded.daily_pay_reduction_multiplier,
                    daily_pay_revenue_target_state_id = excluded.daily_pay_revenue_target_state_id,
                    daily_pay_last_paid_at_epoch_millis = excluded.daily_pay_last_paid_at_epoch_millis,
                    total_level = excluded.total_level,
                    noob_protection_level = excluded.noob_protection_level,
                    projection_payload = excluded.projection_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, projection.computerId)
                statement.setString(2, projection.playerId)
                statement.setString(3, projection.ipAddress)
                statement.setBoolean(4, projection.isNpc)
                statement.setString(5, projection.currentNetworkName)
                statement.setTimestamp(6, projection.lastLoginAt?.let(Timestamp::from))
                statement.setDouble(7, projection.pettyCash)
                statement.setDouble(8, projection.bankMoney)
                setNullableInt(statement, 9, projection.defaultBankPort)
                setNullableInt(statement, 10, projection.defaultRedirectPort)
                statement.setDouble(11, projection.dailyPayBaseAmount)
                statement.setDouble(12, projection.dailyPayReductionMultiplier)
                statement.setString(13, projection.dailyPayRevenueTargetStateId)
                statement.setLong(14, projection.dailyPayLastPaidAtEpochMillis)
                statement.setInt(15, projection.totalLevel)
                statement.setInt(16, projection.noobProtectionLevel)
                statement.setString(17, projection.projectionPayload)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findComputerProjection(computerId: String): PersistedComputerProjection? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    computer_id,
                    player_id,
                    ip_address,
                    is_npc,
                    current_network_name,
                    last_login_at,
                    petty_cash,
                    bank_money,
                    default_bank_port,
                    default_redirect_port,
                    daily_pay_base_amount,
                    daily_pay_reduction_multiplier,
                    daily_pay_revenue_target_state_id,
                    daily_pay_last_paid_at_epoch_millis,
                    total_level,
                    noob_protection_level,
                    projection_payload::text as projection_payload
                from rewrite_computer_projection
                where computer_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, computerId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return mapComputerProjection(resultSet)
                }
            }
        }
    }

    override suspend fun replacePreferences(
        computerId: String,
        preferences: Collection<PersistedComputerPreference>,
    ) {
        connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    delete from rewrite_computer_preference
                    where computer_id = ?
                    """.trimIndent(),
                ).use { deleteStatement ->
                    deleteStatement.setString(1, computerId)
                    deleteStatement.executeUpdate()
                }
                if (preferences.isNotEmpty()) {
                    connection.prepareStatement(
                        """
                        insert into rewrite_computer_preference(
                            computer_id,
                            preference_key,
                            preference_value
                        )
                        values (?, ?, ?)
                        """.trimIndent(),
                    ).use { insertStatement ->
                        preferences.forEach { preference ->
                            insertStatement.setString(1, computerId)
                            insertStatement.setString(2, preference.key)
                            insertStatement.setString(3, preference.value)
                            insertStatement.addBatch()
                        }
                        insertStatement.executeBatch()
                    }
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            }
        }
    }

    override suspend fun listPreferences(computerId: String): List<PersistedComputerPreference> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    computer_id,
                    preference_key,
                    preference_value
                from rewrite_computer_preference
                where computer_id = ?
                order by preference_key asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, computerId)
                statement.executeQuery().use { resultSet ->
                    val preferences = mutableListOf<PersistedComputerPreference>()
                    while (resultSet.next()) {
                        preferences += PersistedComputerPreference(
                            computerId = resultSet.getString("computer_id"),
                            key = resultSet.getString("preference_key"),
                            value = resultSet.getString("preference_value"),
                        )
                    }
                    return preferences
                }
            }
        }
    }

    override suspend fun replaceSkillStats(
        computerId: String,
        stats: Collection<PersistedComputerSkillStat>,
    ) {
        connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    delete from rewrite_computer_skill_stat
                    where computer_id = ?
                    """.trimIndent(),
                ).use { deleteStatement ->
                    deleteStatement.setString(1, computerId)
                    deleteStatement.executeUpdate()
                }
                if (stats.isNotEmpty()) {
                    connection.prepareStatement(
                        """
                        insert into rewrite_computer_skill_stat(
                            computer_id,
                            script_family,
                            experience_points
                        )
                        values (?, ?, ?)
                        """.trimIndent(),
                    ).use { insertStatement ->
                        stats.forEach { stat ->
                            insertStatement.setString(1, computerId)
                            insertStatement.setString(2, stat.scriptFamily.name)
                            insertStatement.setDouble(3, stat.experiencePoints)
                            insertStatement.addBatch()
                        }
                        insertStatement.executeBatch()
                    }
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            }
        }
    }

    override suspend fun listSkillStats(computerId: String): List<PersistedComputerSkillStat> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select
                    computer_id,
                    script_family,
                    experience_points
                from rewrite_computer_skill_stat
                where computer_id = ?
                order by script_family asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, computerId)
                statement.executeQuery().use { resultSet ->
                    val stats = mutableListOf<PersistedComputerSkillStat>()
                    while (resultSet.next()) {
                        stats += PersistedComputerSkillStat(
                            computerId = resultSet.getString("computer_id"),
                            scriptFamily = ScriptFamily.valueOf(resultSet.getString("script_family")),
                            experiencePoints = resultSet.getDouble("experience_points"),
                        )
                    }
                    return stats
                }
            }
        }
    }

    private fun mapPlayerProfile(resultSet: java.sql.ResultSet): PersistedPlayerProfile {
        return PersistedPlayerProfile(
            playerId = resultSet.getString("player_id"),
            displayName = resultSet.getString("display_name"),
            profileImagePath = resultSet.getString("profile_image_path"),
            profileDescription = resultSet.getString("profile_description"),
            profileLocation = resultSet.getString("profile_location"),
            profilePayload = resultSet.getString("profile_payload"),
        )
    }

    private fun mapComputerProjection(resultSet: java.sql.ResultSet): PersistedComputerProjection {
        return PersistedComputerProjection(
            computerId = resultSet.getString("computer_id"),
            playerId = resultSet.getString("player_id"),
            ipAddress = resultSet.getString("ip_address"),
            isNpc = resultSet.getBoolean("is_npc"),
            currentNetworkName = resultSet.getString("current_network_name"),
            lastLoginAt = resultSet.getTimestamp("last_login_at")?.toInstant(),
            pettyCash = resultSet.getDouble("petty_cash"),
            bankMoney = resultSet.getDouble("bank_money"),
            defaultBankPort = resultSet.getIntOrNull("default_bank_port"),
            defaultRedirectPort = resultSet.getIntOrNull("default_redirect_port"),
            dailyPayBaseAmount = resultSet.getDouble("daily_pay_base_amount"),
            dailyPayReductionMultiplier = resultSet.getDouble("daily_pay_reduction_multiplier"),
            dailyPayRevenueTargetStateId = resultSet.getString("daily_pay_revenue_target_state_id"),
            dailyPayLastPaidAtEpochMillis = resultSet.getLong("daily_pay_last_paid_at_epoch_millis"),
            totalLevel = resultSet.getInt("total_level"),
            noobProtectionLevel = resultSet.getInt("noob_protection_level"),
            projectionPayload = resultSet.getString("projection_payload"),
        )
    }

    private fun setNullableInt(
        statement: java.sql.PreparedStatement,
        parameterIndex: Int,
        value: Int?,
    ) {
        if (value == null) {
            statement.setNull(parameterIndex, java.sql.Types.INTEGER)
        } else {
            statement.setInt(parameterIndex, value)
        }
    }

    private fun java.sql.ResultSet.getIntOrNull(columnName: String): Int? {
        val value = getInt(columnName)
        return if (wasNull()) {
            null
        } else {
            value
        }
    }
}
