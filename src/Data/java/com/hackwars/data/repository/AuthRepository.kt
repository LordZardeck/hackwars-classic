package com.hackwars.data.repository

import com.hackwars.data.model.ForumLoginSnapshot
import jakarta.persistence.EntityManager

class AuthRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT password FROM hackerforum.users WHERE name=? AND password=PASSWORD(?) AND ip=?
     */
    fun forumPasswordMatches(username: String, plainPassword: String, ip: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT password
            FROM hackerforum.users
            WHERE name = :username
              AND password = PASSWORD(:password)
              AND ip = :ip
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setParameter("password", plainPassword)
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList as List<String>
        return rows.isNotEmpty()
    }

    /**
     * Legacy mapping:
     * SELECT name FROM hackwars_drupal.users WHERE name=? AND pass=PASSWORD(?)
     */
    fun drupalPasswordMatches(username: String, plainPassword: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT name
            FROM hackwars_drupal.users
            WHERE name = :username
              AND pass = PASSWORD(:password)
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setParameter("password", plainPassword)
            .setMaxResults(1)
            .resultList as List<String>
        return rows.isNotEmpty()
    }

    /**
     * Legacy mapping:
     * SELECT name FROM hackerforum.users WHERE name=?
     */
    fun forumUserExists(username: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT name
            FROM hackerforum.users
            WHERE name = :username
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setMaxResults(1)
            .resultList as List<String>
        return rows.isNotEmpty()
    }

    /**
     * Legacy mapping:
     * SELECT ip, npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in) FROM hackerforum.users WHERE name=?
     */
    fun findForumLoginSnapshotByName(username: String): ForumLoginSnapshot? {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT ip, npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in)
            FROM hackerforum.users
            WHERE name = :username
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setMaxResults(1)
            .resultList as List<Array<Any?>>
        val row = rows.firstOrNull() ?: return null
        val days = (row[2] as? Number)?.toInt()
        return ForumLoginSnapshot(ip = row[0] as String, npc = row[1] as String, daysSinceLastLogin = days)
    }

    /**
     * Legacy mapping:
     * SELECT npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in) FROM hackerforum.users WHERE ip=?
     */
    fun findForumActivityByIp(ip: String): Pair<String, Int?>? {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in)
            FROM hackerforum.users
            WHERE ip = :ip
            """.trimIndent(),
        )
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList as List<Array<Any?>>
        val row = rows.firstOrNull() ?: return null
        return (row[0] as String) to ((row[1] as? Number)?.toInt())
    }

    /**
     * Legacy mapping:
     * UPDATE hackerforum.users SET last_logged_in=NOW() WHERE name=?
     */
    fun updateForumLastLoggedInNow(username: String): Int =
        entityManager.createNativeQuery(
            """
            UPDATE hackerforum.users
            SET last_logged_in = NOW()
            WHERE name = :username
            """.trimIndent(),
        )
            .setParameter("username", username)
            .executeUpdate()

    /**
     * Legacy mapping:
     * SELECT uid FROM hackwars_drupal.users WHERE ip=?
     */
    fun findDrupalUidByIp(ip: String): Long? {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT uid
            FROM hackwars_drupal.users
            WHERE ip = :ip
            """.trimIndent(),
        )
            .setParameter("ip", ip)
            .setMaxResults(1)
            .resultList as List<Number>
        return rows.firstOrNull()?.toLong()
    }
}
