package com.hackwars.data.repository

import com.hackwars.data.model.SearchBootstrapRow
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets

class SearchBootstrapRepository(private val entityManager: EntityManager) {
    fun findBootstrapRows(): List<SearchBootstrapRow> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT u.stats, u.ip, TO_DAYS(NOW()) - TO_DAYS(f.last_logged_in), f.npc
            FROM hackwars.user u
            LEFT JOIN hackerforum.users f
                ON f.ip = u.ip
            ORDER BY u.num
            """.trimIndent(),
        ).resultList as List<Array<Any?>>

        return rows.mapNotNull { row ->
            val statsXml = when (val value = row[0]) {
                is ByteArray -> String(value, StandardCharsets.UTF_8)
                is String -> value
                else -> value?.toString()
            } ?: return@mapNotNull null
            val ip = row[1]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SearchBootstrapRow(
                statsXml = statsXml,
                ip = ip,
                daysSinceLastLogin = (row[2] as? Number)?.toInt(),
                npc = row[3]?.toString(),
            )
        }
    }
}
