package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.DomainEntity
import jakarta.persistence.EntityManager

class DomainRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT domain FROM domains WHERE ip=?
     */
    fun findDomainByIp(ip: String): String? =
        entityManager.createQuery(
            """
            SELECT d
            FROM DomainEntity d
            WHERE d.ip = :ip
            ORDER BY d.num ASC
            """.trimIndent(),
            DomainEntity::class.java,
        )
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
            ?.domain
}
