package com.hackwars.data.entity.hackwars

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "user", schema = "hackwars")
open class UserSaveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "num")
    open var num: Int? = null

    @Column(name = "ip")
    open var ip: String? = null

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "stats")
    open var stats: ByteArray? = null
}

@Embeddable
class UserPlayStatisticId() : Serializable {
    @Column(name = "uid")
    var uid: Long? = null

    @Column(name = "starttime")
    var startTime: Long? = null

    @Column(name = "endtime")
    var endTime: Long? = null

    constructor(uid: Long, startTime: Long, endTime: Long) : this() {
        this.uid = uid
        this.startTime = startTime
        this.endTime = endTime
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPlayStatisticId) return false
        return uid == other.uid && startTime == other.startTime && endTime == other.endTime
    }

    override fun hashCode(): Int {
        var result = uid?.hashCode() ?: 0
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (endTime?.hashCode() ?: 0)
        return result
    }
}

@Entity
@Table(name = "user_play_statistics", schema = "hackwars")
open class UserPlayStatisticEntity {
    @EmbeddedId
    open var id: UserPlayStatisticId = UserPlayStatisticId()
}

/**
 * Not present in local schema, but kept for mapping of existing legacy SQL callsites.
 */
@Entity
@Table(name = "bought_items", schema = "hackwars")
open class BoughtItemEntity {
    @Id
    @Column(name = "bought_item_id")
    open var boughtItemId: Long? = null

    @Column(name = "store_item_id")
    open var storeItemId: Int? = null

    @Column(name = "ip")
    open var ip: String? = null

    @Column(name = "given")
    open var given: Int? = null
}
