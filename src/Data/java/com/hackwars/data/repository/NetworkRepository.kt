package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.NetworkEntity
import com.hackwars.data.entity.hackwars.NetworkNpcEntity
import com.hackwars.data.model.AttachedNetworkLink
import com.hackwars.data.model.NetworkNpcView
import com.hackwars.data.model.NetworkSummary
import jakarta.persistence.EntityManager

class NetworkRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * SELECT id, name, attack_probability FROM network
     */
    fun findNetworks(): List<NetworkSummary> =
        entityManager.createQuery(
            """
            SELECT n
            FROM NetworkEntity n
            ORDER BY n.id
            """.trimIndent(),
            NetworkEntity::class.java,
        )
            .resultList
            .map {
                NetworkSummary(
                    id = it.id ?: 0,
                    name = it.name,
                    attackProbability = it.attackProbability,
                )
            }

    /**
     * Legacy mapping:
     * SELECT n.name, an.entranceMessage FROM network n
     * INNER JOIN attached_networks an ON n.id = an.attached_network_id
     * WHERE an.network_id = ?
     */
    fun findAttachedNetworks(networkId: Int): List<AttachedNetworkLink> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT n.name, an.entranceMessage
            FROM hackwars.network n
            INNER JOIN hackwars.attached_networks an
                ON n.id = an.attached_network_id
            WHERE an.network_id = :networkId
            """.trimIndent(),
        )
            .setParameter("networkId", networkId)
            .resultList as List<Array<Any?>>

        return rows.map {
            AttachedNetworkLink(
                attachedNetworkName = it[0] as String,
                entranceMessage = it[1] as String,
            )
        }
    }

    /**
     * Legacy mapping:
     * SELECT npc_ip, [resource], name, title FROM network_npc WHERE npc_type=? AND network_id=?
     */
    fun findNpcsByType(networkId: Int, npcType: String): List<NetworkNpcView> =
        entityManager.createQuery(
            """
            SELECT npc
            FROM NetworkNpcEntity npc
            WHERE npc.id.networkId = :networkId
              AND npc.id.npcType = :npcType
            ORDER BY npc.id.npcIp
            """.trimIndent(),
            NetworkNpcEntity::class.java,
        )
            .setParameter("networkId", networkId)
            .setParameter("npcType", npcType)
            .resultList
            .map {
                NetworkNpcView(
                    ip = it.id.npcIp ?: "",
                    type = it.id.npcType ?: "",
                    resource = it.resource,
                    name = it.name,
                    title = it.title,
                )
            }
}
