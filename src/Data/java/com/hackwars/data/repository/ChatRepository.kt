package com.hackwars.data.repository

import com.hackwars.data.entity.alexchat.ChatUserEntity
import com.hackwars.data.model.ChatRelationRow
import jakarta.persistence.EntityManager

class ChatRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT userkey FROM user WHERE username = ?
     */
    fun findUserKey(username: String): Int? =
        entityManager.createQuery(
            """
            SELECT u
            FROM ChatUserEntity u
            WHERE u.username = :username
            """.trimIndent(),
            ChatUserEntity::class.java,
        )
            .setParameter("username", username)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
            ?.userKey

    /**
     * Legacy mapping:
     * SELECT userkey, properties, usernick FROM user WHERE username = ?
     */
    fun findUser(username: String): ChatUserEntity? =
        entityManager.createQuery(
            """
            SELECT u
            FROM ChatUserEntity u
            WHERE u.username = :username
            """.trimIndent(),
            ChatUserEntity::class.java,
        )
            .setParameter("username", username.lowercase())
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    /**
     * Legacy mapping:
     * INSERT INTO user(username,usernick) VALUES(?, ?)
     *
     * Caller should run inside a transaction.
     */
    fun createUser(username: String, userNick: String): Int =
        entityManager.createNativeQuery(
            """
            INSERT INTO alex_chat.user (USERNAME, USERNICK)
            VALUES (:username, :userNick)
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setParameter("userNick", userNick)
            .executeUpdate()

    /**
     * Legacy mapping:
     * UPDATE user SET usernick = ?, properties = ? WHERE username = ?
     */
    fun updateUser(username: String, userNick: String, propertiesSql: String): Int =
        entityManager.createNativeQuery(
            """
            UPDATE alex_chat.user
            SET USERNICK = :userNick,
                PROPERTIES = :properties
            WHERE USERNAME = :username
            """.trimIndent(),
        )
            .setParameter("username", username)
            .setParameter("userNick", userNick)
            .setParameter("properties", propertiesSql)
            .executeUpdate()

    /**
     * Legacy mapping:
     * DELETE FROM user WHERE username = ?
     */
    fun deleteUser(username: String): Int =
        entityManager.createNativeQuery(
            """
            DELETE FROM alex_chat.user
            WHERE USERNAME = :username
            """.trimIndent(),
        )
            .setParameter("username", username)
            .executeUpdate()

    /**
     * Legacy mapping:
     * SELECT u.username, ur.comment, ur.relation
     * FROM userrelation ur, user u
     * WHERE ur.usera = (SELECT userkey FROM user WHERE username = ?)
     *   AND ur.userb = u.userkey
     */
    fun findRelations(mainUsername: String): List<ChatRelationRow> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT u.USERNAME, ur.COMMENT, ur.RELATION
            FROM alex_chat.userrelation ur
            INNER JOIN alex_chat.user u ON ur.USERB = u.USERKEY
            WHERE ur.USERA = (
                SELECT us.USERKEY
                FROM alex_chat.user us
                WHERE us.USERNAME = :mainUsername
                LIMIT 1
            )
            """.trimIndent(),
        )
            .setParameter("mainUsername", mainUsername)
            .resultList as List<Array<Any?>>
        return rows.map { row ->
            ChatRelationRow(
                username = row[0] as String,
                comment = row[1] as String?,
                relation = row[2]?.toString(),
            )
        }
    }

    /**
     * Legacy mapping:
     * INSERT INTO userrelation(usera,userb,comment)
     * VALUES ((SELECT key FROM user WHERE username=?),(SELECT key FROM user WHERE username=?),?)
     */
    fun createRelation(mainUsername: String, secondaryUsername: String, comment: String): Int =
        entityManager.createNativeQuery(
            """
            INSERT INTO alex_chat.userrelation (USERA, USERB, COMMENT)
            VALUES (
                (SELECT USERKEY FROM alex_chat.user WHERE USERNAME = :mainUsername LIMIT 1),
                (SELECT USERKEY FROM alex_chat.user WHERE USERNAME = :secondaryUsername LIMIT 1),
                :comment
            )
            """.trimIndent(),
        )
            .setParameter("mainUsername", mainUsername)
            .setParameter("secondaryUsername", secondaryUsername)
            .setParameter("comment", comment)
            .executeUpdate()

    /**
     * Legacy mapping:
     * UPDATE userrelation SET comment=?, relation=?
     * WHERE usera=(SELECT key FROM user WHERE username=?)
     *   AND userb=(SELECT key FROM user WHERE username=?)
     */
    fun updateRelation(mainUsername: String, secondaryUsername: String, comment: String, relation: String?): Int =
        entityManager.createNativeQuery(
            """
            UPDATE alex_chat.userrelation
            SET COMMENT = :comment,
                RELATION = :relation
            WHERE USERA = (
                SELECT USERKEY FROM alex_chat.user
                WHERE USERNAME = :mainUsername
                LIMIT 1
            )
              AND USERB = (
                SELECT USERKEY FROM alex_chat.user
                WHERE USERNAME = :secondaryUsername
                LIMIT 1
            )
            """.trimIndent(),
        )
            .setParameter("mainUsername", mainUsername)
            .setParameter("secondaryUsername", secondaryUsername)
            .setParameter("comment", comment)
            .setParameter("relation", relation)
            .executeUpdate()
}
