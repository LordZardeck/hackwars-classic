package com.hackwars.data.entity.hackerforum

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users", schema = "hackerforum")
open class ForumUserEntity {
    @Id
    @Column(name = "num")
    open var num: Long? = null

    @Column(name = "name", nullable = false)
    open var name: String = ""

    @Column(name = "password", nullable = false)
    open var password: String = ""

    @Column(name = "ip", nullable = false)
    open var ip: String = ""

    @Column(name = "npc", nullable = false)
    open var npc: String = ""

    @Column(name = "last_logged_in", nullable = false)
    open var lastLoggedIn: String = ""
}
