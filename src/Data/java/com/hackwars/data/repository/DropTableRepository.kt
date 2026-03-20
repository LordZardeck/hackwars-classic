package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.DropTableEntryEntity
import com.hackwars.data.entity.hackwars.ItemEntity
import com.hackwars.data.model.DropEntry
import com.hackwars.data.model.DropItemData
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets

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

    fun findDropItems(dropId: Int): List<DropItemData> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT dt.weight, i.data
            FROM hackwars.drop_table dt
            INNER JOIN hackwars.items i
                ON i.id = dt.item_id
            WHERE dt.drop_id = :dropId
            ORDER BY dt.item_id
            """.trimIndent(),
        )
            .setParameter("dropId", dropId)
            .resultList as List<Array<Any?>>

        return rows.mapNotNull { row ->
            val weight = (row[0] as? Number)?.toInt() ?: return@mapNotNull null
            val data = when (val value = row[1]) {
                is ByteArray -> String(value, StandardCharsets.UTF_8)
                is String -> value
                else -> value?.toString()
            } ?: return@mapNotNull null
            DropItemData(weight = weight, data = data)
        }
    }
}
