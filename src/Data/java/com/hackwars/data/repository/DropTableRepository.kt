package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.DropTableEntryEntity
import com.hackwars.data.entity.hackwars.ItemEntity
import com.hackwars.data.model.DropEntry
import jakarta.persistence.EntityManager

class DropTableRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT item_id,weight FROM drop_table WHERE drop_id=?
     */
    fun findDropEntries(dropId: Int): List<DropEntry> =
        entityManager.createQuery(
            """
            SELECT d
            FROM DropTableEntryEntity d
            WHERE d.id.dropId = :dropId
            ORDER BY d.id.itemId
            """.trimIndent(),
            DropTableEntryEntity::class.java,
        )
            .setParameter("dropId", dropId)
            .resultList
            .map { DropEntry(itemId = it.id.itemId, weight = it.id.weight) }

    /**
     * Legacy mapping:
     * SELECT data FROM items WHERE id=?
     */
    fun findItemDataById(itemId: Int): ByteArray? =
        entityManager.createQuery(
            """
            SELECT i
            FROM ItemEntity i
            WHERE i.id = :itemId
            """.trimIndent(),
            ItemEntity::class.java,
        )
            .setParameter("itemId", itemId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
            ?.data
}
