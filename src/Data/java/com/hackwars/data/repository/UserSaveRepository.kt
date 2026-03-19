package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.UserSaveEntity
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets

class UserSaveRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * select stats from user where ip = ? limit 1
     */
    fun findStatsXmlByIp(ip: String): String? {
        val rows = entityManager.createQuery(
            """
            SELECT u
            FROM UserSaveEntity u
            WHERE u.ip = :ip
            ORDER BY u.num ASC
            """.trimIndent(),
            UserSaveEntity::class.java,
        )
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList
        val statsBytes = rows.firstOrNull()?.stats ?: return null
        return String(statsBytes, StandardCharsets.UTF_8)
    }

    /**
     * Legacy insert/update mapping:
     * insert into user values(...)
     * update user set stats=? where ip=?
     *
     * Call inside a transaction.
     */
    fun upsertStatsXmlByIp(ip: String, xml: String?) {
        val existing = entityManager.createQuery(
            """
            SELECT u
            FROM UserSaveEntity u
            WHERE u.ip = :ip
            ORDER BY u.num ASC
            """.trimIndent(),
            UserSaveEntity::class.java,
        )
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

        val xmlBytes = xml?.toByteArray(StandardCharsets.UTF_8)
        if (existing == null) {
            val created = UserSaveEntity()
            created.ip = ip
            created.stats = xmlBytes
            entityManager.persist(created)
            return
        }

        existing.stats = xmlBytes
        entityManager.merge(existing)
    }
}
