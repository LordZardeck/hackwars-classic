package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.NetworkDirectoryDefinition
import com.hackwars.rewrite.gamecore.NetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.NetworkSwitchValidation
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.NpcDirectoryEntry
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import java.sql.Connection

class JdbcNetworkDirectoryRepository(
    private val connectionFactory: () -> Connection,
) : NetworkDirectoryRepository {
    override suspend fun loadNetwork(name: String): NetworkDirectoryDefinition? {
        return connectionFactory().use { connection ->
            loadNetwork(connection, name)
        }
    }

    override suspend fun validateSwitch(
        fromNetwork: String,
        toNetwork: String,
        allowedNetworks: Set<String>,
    ): NetworkSwitchValidation {
        return connectionFactory().use { connection ->
            validateSwitch(connection, fromNetwork, toNetwork, allowedNetworks)
        }
    }

    companion object {
        internal fun loadNetwork(
            connection: Connection,
            name: String,
        ): NetworkDirectoryDefinition? {
            connection.prepareStatement(
                """
                select network_name, store_state_id
                from rewrite_network_directory
                where network_name = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }

                    val networkName = resultSet.getString("network_name")
                    return NetworkDirectoryDefinition(
                        name = networkName,
                        storeStateId = resultSet.getString("store_state_id")?.let(::GameStateId),
                        regularNpcs = loadNpcEntries(connection, networkName, NpcCategory.REGULAR),
                        questNpcs = loadNpcEntries(connection, networkName, NpcCategory.QUEST),
                        miningNpcs = loadNpcEntries(connection, networkName, NpcCategory.MINING),
                        storeNpcs = loadNpcEntries(connection, networkName, NpcCategory.STORE),
                        switchMessagesByTarget = loadFailureMessages(connection, networkName),
                    )
                }
            }
        }

        internal fun validateSwitch(
            connection: Connection,
            fromNetwork: String,
            toNetwork: String,
            allowedNetworks: Set<String>,
        ): NetworkSwitchValidation {
            if (toNetwork == ROOT_NETWORK_NAME || allowedNetworks.contains(toNetwork)) {
                return NetworkSwitchValidation(
                    allowed = true,
                    failureMessage = "",
                )
            }

            connection.prepareStatement(
                """
                select failure_message
                from rewrite_network_link
                where from_network_name = ?
                  and to_network_name = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, fromNetwork)
                statement.setString(2, toNetwork)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return NetworkSwitchValidation(
                            allowed = false,
                            failureMessage = resultSet.getString("failure_message"),
                        )
                    }
                }
            }

            return NetworkSwitchValidation(
                allowed = false,
                failureMessage = "There is no connection between $fromNetwork and $toNetwork.",
            )
        }

        private fun loadNpcEntries(
            connection: Connection,
            networkName: String,
            category: NpcCategory,
        ): List<NpcDirectoryEntry> {
            connection.prepareStatement(
                """
                select state_id, display_name, title, commodity
                from rewrite_network_npc
                where network_name = ?
                  and category = ?
                order by sort_order asc, state_id asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, networkName)
                statement.setString(2, category.name)
                statement.executeQuery().use { resultSet ->
                    val entries = mutableListOf<NpcDirectoryEntry>()
                    while (resultSet.next()) {
                        entries += NpcDirectoryEntry(
                            stateId = GameStateId(resultSet.getString("state_id")),
                            displayName = resultSet.getString("display_name"),
                            title = resultSet.getString("title").orEmpty(),
                            category = category,
                            commodity = resultSet.getString("commodity"),
                        )
                    }
                    return entries
                }
            }
        }

        private fun loadFailureMessages(
            connection: Connection,
            networkName: String,
        ): Map<String, String> {
            connection.prepareStatement(
                """
                select to_network_name, failure_message
                from rewrite_network_link
                where from_network_name = ?
                order by to_network_name asc
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, networkName)
                statement.executeQuery().use { resultSet ->
                    val messages = linkedMapOf<String, String>()
                    while (resultSet.next()) {
                        messages[resultSet.getString("to_network_name")] = resultSet.getString("failure_message")
                    }
                    return messages
                }
            }
        }
    }
}
