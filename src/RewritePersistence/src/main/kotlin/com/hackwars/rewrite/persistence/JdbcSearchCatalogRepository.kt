package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.SearchCatalogRepository
import com.hackwars.rewrite.gamecore.SearchableWebsiteDocument
import java.sql.Connection

class JdbcSearchCatalogRepository(
    private val connectionFactory: () -> Connection,
    private val serializer: ComputerStateSerializer = ComputerStateSerializer(),
) : SearchCatalogRepository {
    override suspend fun loadDocuments(): List<SearchableWebsiteDocument> {
        return connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                select state_payload::text
                from rewrite_computer_state
                order by ip_address asc
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val documents = mutableListOf<SearchableWebsiteDocument>()
                    while (resultSet.next()) {
                        val state = serializer.decodeStateJson(resultSet.getString(1))
                        documents += SearchableWebsiteDocument(
                            address = state.identity.playerIp.trim().lowercase(),
                            title = state.website.title,
                            body = state.website.body,
                            searchable = state.ports.any { port ->
                                port.defaultPort &&
                                    port.enabled &&
                                    port.installedApplication?.kind == ApplicationKind.HTTP
                            },
                            lastLoginAtEpochMillis = state.identity.lastLoginAtEpochMillis,
                            isNpc = state.identity.isNpc,
                        )
                    }
                    documents
                }
            }
        }
    }
}
