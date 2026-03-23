package com.hackwars.data.repository

import com.hackwars.data.model.SearchBootstrapRow
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets

class SearchBootstrapRepository(private val entityManager: EntityManager) {
    fun findBootstrapRows(): List<SearchBootstrapRow> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                u.num,
                u.stats,
                u.stats_json,
                u.stats_json_version,
                body_blob.text_content,
                u.ip,
                TO_DAYS(NOW()) - TO_DAYS(f.last_logged_in),
                f.npc
            FROM hackwars.user u
            LEFT JOIN hackwars.user_stats_text_blob body_blob
                ON body_blob.user_num = u.num
                AND body_blob.blob_path = 'website/body'
            LEFT JOIN hackerforum.users f
                ON f.ip = u.ip
            ORDER BY u.num
            """.trimIndent(),
        ).resultList as List<Array<Any?>>

        return rows.mapNotNull { row ->
            val userNum = (row[0] as? Number)?.toInt() ?: return@mapNotNull null
            val statsXml = when (val value = row[1]) {
                is ByteArray -> String(value, StandardCharsets.UTF_8)
                is String -> value
                else -> value?.toString()
            }
            val ip = row[5]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SearchBootstrapRow(
                userNum = userNum,
                statsXml = statsXml,
                statsJson = row[2]?.toString(),
                statsJsonVersion = (row[3] as? Number)?.toInt(),
                websiteBodyText = row[4]?.toString(),
                ip = ip,
                daysSinceLastLogin = (row[6] as? Number)?.toInt(),
                npc = row[7]?.toString(),
            )
        }
    }
}
