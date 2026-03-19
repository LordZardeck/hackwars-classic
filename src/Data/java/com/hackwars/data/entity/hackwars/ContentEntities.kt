package com.hackwars.data.entity.hackwars

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "domains", schema = "hackwars")
open class DomainEntity {
    @Id
    @Column(name = "num")
    open var num: Int? = null

    @Column(name = "ip", nullable = false)
    open var ip: String = ""

    @Column(name = "domain", nullable = false)
    open var domain: String = ""
}

@Embeddable
class DropTableEntryId() : Serializable {
    @Column(name = "drop_id")
    var dropId: Int? = null

    @Column(name = "item_id")
    var itemId: Int? = null

    @Column(name = "weight")
    var weight: Int? = null

    constructor(dropId: Int?, itemId: Int?, weight: Int?) : this() {
        this.dropId = dropId
        this.itemId = itemId
        this.weight = weight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropTableEntryId) return false
        return dropId == other.dropId && itemId == other.itemId && weight == other.weight
    }

    override fun hashCode(): Int {
        var result = dropId ?: 0
        result = 31 * result + (itemId ?: 0)
        result = 31 * result + (weight ?: 0)
        return result
    }
}

@Entity
@Table(name = "drop_table", schema = "hackwars")
open class DropTableEntryEntity {
    @EmbeddedId
    open var id: DropTableEntryId = DropTableEntryId()
}

@Entity
@Table(name = "items", schema = "hackwars")
open class ItemEntity {
    @Id
    @Column(name = "id")
    open var id: Int? = null

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data")
    open var data: ByteArray? = null

    @Column(name = "description")
    open var description: String? = null
}
