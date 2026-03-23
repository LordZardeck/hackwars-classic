package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.UserStatsTextBlobEntity
import com.hackwars.data.model.JsonProfileWrite
import com.hackwars.data.model.PersistedProfileSave
import com.hackwars.data.model.PersistedTextBlob
import com.hackwars.data.entity.hackwars.UserSaveEntity
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets

class UserSaveRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * select stats from user where ip = ? limit 1
     */
    fun findStatsXmlByIp(ip: String): String? {
        return findPersistedProfileByIp(ip)?.legacyXml
    }

    fun findPersistedProfileByIp(ip: String): PersistedProfileSave? {
        val entity = findFirstByIp(ip) ?: return null
        val userNum = entity.num ?: return null
        return toPersistedProfile(entity, findTextBlobsByUserNum(userNum))
    }

    fun findTextBlobsByUserNum(userNum: Int): List<PersistedTextBlob> {
        return entityManager.createQuery(
            """
            SELECT b
            FROM UserStatsTextBlobEntity b
            WHERE b.userNum = :userNum
            ORDER BY b.blobPath ASC
            """.trimIndent(),
            UserStatsTextBlobEntity::class.java,
        )
            .setParameter("userNum", userNum)
            .resultList
            .mapNotNull { blob ->
                val path = blob.blobPath ?: return@mapNotNull null
                val kind = blob.blobKind ?: return@mapNotNull null
                PersistedTextBlob(
                    path = path,
                    kind = kind,
                    textContent = blob.textContent ?: "",
                )
            }
    }

    /**
     * Legacy insert/update mapping:
     * insert into user values(...)
     * update user set stats=? where ip=?
     *
     * Call inside a transaction.
     */
    fun upsertStatsXmlByIp(ip: String, xml: String?) {
        val existing = findFirstByIp(ip)

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

    fun upsertJsonByIp(ip: String, write: JsonProfileWrite) {
        val existing = findFirstByIp(ip)
        val entity = if (existing != null) {
            existing
        } else {
            UserSaveEntity().also {
                it.ip = ip
                entityManager.persist(it)
                entityManager.flush()
            }
        }

        entity.statsJson = write.manifestJson
        entity.statsJsonVersion = write.version
        entity.statsJsonMigratedAt = write.migratedAt
        entityManager.merge(entity)

        val userNum = entity.num
            ?: error("Unable to determine user.num for ip=$ip while upserting stats_json")
        replaceTextBlobs(userNum, write.blobs)
    }

    private fun findFirstByIp(ip: String): UserSaveEntity? {
        return entityManager.createQuery(
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
    }

    private fun replaceTextBlobs(userNum: Int, blobs: List<PersistedTextBlob>) {
        entityManager.createQuery(
            """
            DELETE
            FROM UserStatsTextBlobEntity b
            WHERE b.userNum = :userNum
            """.trimIndent(),
        )
            .setParameter("userNum", userNum)
            .executeUpdate()

        blobs.forEach { blob ->
            val entity = UserStatsTextBlobEntity()
            entity.userNum = userNum
            entity.blobPath = blob.path
            entity.blobKind = blob.kind
            entity.textContent = blob.textContent
            entityManager.persist(entity)
        }
    }

    private fun toPersistedProfile(entity: UserSaveEntity, blobs: List<PersistedTextBlob>): PersistedProfileSave {
        val userNum = entity.num ?: error("Expected user.num to be populated")
        return PersistedProfileSave(
            userNum = userNum,
            ip = entity.ip.orEmpty(),
            legacyXml = entity.stats?.let { String(it, StandardCharsets.UTF_8) },
            statsJson = entity.statsJson,
            statsJsonVersion = entity.statsJsonVersion,
            statsJsonMigratedAt = entity.statsJsonMigratedAt,
            blobs = blobs,
        )
    }
}
