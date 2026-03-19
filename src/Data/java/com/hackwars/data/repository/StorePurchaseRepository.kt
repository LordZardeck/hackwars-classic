package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.BoughtItemEntity
import com.hackwars.data.model.PendingPurchase
import jakarta.persistence.EntityManager

class StorePurchaseRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT store_item_id,bought_item_id FROM bought_items WHERE ip=? AND given=0
     */
    fun findPendingPurchasesByIp(ip: String): List<PendingPurchase> =
        entityManager.createQuery(
            """
            SELECT b
            FROM BoughtItemEntity b
            WHERE b.ip = :ip
              AND b.given = 0
            ORDER BY b.boughtItemId
            """.trimIndent(),
            BoughtItemEntity::class.java,
        )
            .setParameter("ip", ip)
            .resultList
            .mapNotNull { b ->
                val boughtId = b.boughtItemId ?: return@mapNotNull null
                PendingPurchase(storeItemId = b.storeItemId, boughtItemId = boughtId)
            }

    /**
     * Legacy mapping:
     * UPDATE bought_items SET given=1 WHERE bought_item_id=?
     */
    fun markAsGiven(boughtItemId: Long): Int =
        entityManager.createQuery(
            """
            UPDATE BoughtItemEntity b
            SET b.given = 1
            WHERE b.boughtItemId = :boughtItemId
            """.trimIndent(),
        )
            .setParameter("boughtItemId", boughtItemId)
            .executeUpdate()
}
