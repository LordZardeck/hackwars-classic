package com.hackwars.data.entity.hackwarsdrupal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users", schema = "hackwars_drupal")
open class DrupalUserEntity {
    @Id
    @Column(name = "uid")
    open var uid: Long? = null

    @Column(name = "name", nullable = false)
    open var name: String = ""

    @Column(name = "pass", nullable = false)
    open var pass: String = ""

    @Column(name = "ip", nullable = false)
    open var ip: String = ""
}
