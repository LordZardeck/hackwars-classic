package com.hackwars.data.entity.hackwars

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "network", schema = "hackwars")
open class NetworkEntity {
    @Id
    @Column(name = "id")
    open var id: Int? = null

    @Column(name = "name", nullable = false)
    open var name: String = ""

    @Column(name = "storeNPC", nullable = false)
    open var storeNpc: String = ""

    @Column(name = "attack_probability", nullable = false)
    open var attackProbability: Float = 0f
}

@Embeddable
class AttachedNetworkId() : Serializable {
    @Column(name = "network_id")
    var networkId: Int? = null

    @Column(name = "attached_network_id")
    var attachedNetworkId: Int? = null

    constructor(networkId: Int?, attachedNetworkId: Int?) : this() {
        this.networkId = networkId
        this.attachedNetworkId = attachedNetworkId
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachedNetworkId) return false
        return networkId == other.networkId && attachedNetworkId == other.attachedNetworkId
    }

    override fun hashCode(): Int {
        var result = networkId ?: 0
        result = 31 * result + (attachedNetworkId ?: 0)
        return result
    }
}

@Entity
@Table(name = "attached_networks", schema = "hackwars")
open class AttachedNetworkEntity {
    @EmbeddedId
    open var id: AttachedNetworkId = AttachedNetworkId()

    @Column(name = "entranceMessage", nullable = false)
    open var entranceMessage: String = ""
}

@Embeddable
class NetworkNpcId() : Serializable {
    @Column(name = "network_id")
    var networkId: Int? = null

    @Column(name = "npc_ip")
    var npcIp: String? = null

    @Column(name = "npc_type")
    var npcType: String? = null

    constructor(networkId: Int?, npcIp: String?, npcType: String?) : this() {
        this.networkId = networkId
        this.npcIp = npcIp
        this.npcType = npcType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetworkNpcId) return false
        return networkId == other.networkId && npcIp == other.npcIp && npcType == other.npcType
    }

    override fun hashCode(): Int {
        var result = networkId ?: 0
        result = 31 * result + (npcIp?.hashCode() ?: 0)
        result = 31 * result + (npcType?.hashCode() ?: 0)
        return result
    }
}

@Entity
@Table(name = "network_npc", schema = "hackwars")
open class NetworkNpcEntity {
    @EmbeddedId
    open var id: NetworkNpcId = NetworkNpcId()

    @Column(name = "resource", nullable = false)
    open var resource: String = ""

    @Column(name = "name", nullable = false)
    open var name: String = ""

    @Column(name = "title", nullable = false)
    open var title: String = ""
}
