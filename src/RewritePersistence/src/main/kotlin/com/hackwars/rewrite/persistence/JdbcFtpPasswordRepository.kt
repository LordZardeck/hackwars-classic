package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.FtpPasswordRepository
import com.hackwars.rewrite.gamecore.GameStateId
import java.sql.Connection

private const val FTP_PASSWORD_PREFERENCE_KEY = "__ftp_password"

class JdbcFtpPasswordRepository(
    private val connectionFactory: () -> Connection,
) : FtpPasswordRepository {
    override suspend fun load(stateId: GameStateId): String? {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select preference_value
                from rewrite_computer_preference
                where computer_id = ?
                  and preference_key = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, stateId.value)
                statement.setString(2, FTP_PASSWORD_PREFERENCE_KEY)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return resultSet.getString("preference_value")?.takeUnless { it.isEmpty() }
                }
            }
        }
    }

    override suspend fun save(stateId: GameStateId, password: String?) {
        val normalizedPassword = password?.takeUnless { it.isEmpty() }
        connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                if (normalizedPassword == null) {
                    connection.prepareStatement(
                        """
                        delete from rewrite_computer_preference
                        where computer_id = ?
                          and preference_key = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, stateId.value)
                        statement.setString(2, FTP_PASSWORD_PREFERENCE_KEY)
                        statement.executeUpdate()
                    }
                } else {
                    connection.prepareStatement(
                        """
                        insert into rewrite_computer_preference(computer_id, preference_key, preference_value)
                        values (?, ?, ?)
                        on conflict (computer_id, preference_key) do update
                        set preference_value = excluded.preference_value
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, stateId.value)
                        statement.setString(2, FTP_PASSWORD_PREFERENCE_KEY)
                        statement.setString(3, normalizedPassword)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            }
        }
    }
}
