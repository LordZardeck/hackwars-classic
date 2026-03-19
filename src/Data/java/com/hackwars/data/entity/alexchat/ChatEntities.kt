package com.hackwars.data.entity.alexchat

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "user", schema = "alex_chat")
open class ChatUserEntity {
    @Id
    @Column(name = "USERNAME")
    open var username: String = ""

    @Column(name = "USERKEY", nullable = false)
    open var userKey: Int? = null

    @Column(name = "USERNICK", nullable = false)
    open var userNick: String = ""

    @Column(name = "PROPERTIES")
    open var properties: String? = null
}

@Embeddable
class ChatUserRelationId() : Serializable {
    @Column(name = "USERA")
    var userA: Int? = null

    @Column(name = "USERB")
    var userB: Int? = null

    constructor(userA: Int, userB: Int) : this() {
        this.userA = userA
        this.userB = userB
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatUserRelationId) return false
        return userA == other.userA && userB == other.userB
    }

    override fun hashCode(): Int {
        var result = userA ?: 0
        result = 31 * result + (userB ?: 0)
        return result
    }
}

@Entity
@Table(name = "userrelation", schema = "alex_chat")
open class ChatUserRelationEntity {
    @EmbeddedId
    open var id: ChatUserRelationId = ChatUserRelationId()

    @Column(name = "COMMENT")
    open var comment: String? = null

    @Column(name = "RELATION")
    open var relation: String? = null
}
