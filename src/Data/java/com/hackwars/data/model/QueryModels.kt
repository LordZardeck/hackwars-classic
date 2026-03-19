package com.hackwars.data.model

data class ForumLoginSnapshot(
    val ip: String,
    val npc: String,
    val daysSinceLastLogin: Int?,
)

data class NetworkSummary(
    val id: Int,
    val name: String,
    val attackProbability: Float,
)

data class AttachedNetworkLink(
    val attachedNetworkName: String,
    val entranceMessage: String,
)

data class NetworkNpcView(
    val ip: String,
    val type: String,
    val resource: String,
    val name: String,
    val title: String,
)

data class DropEntry(
    val itemId: Int?,
    val weight: Int?,
)

data class PendingPurchase(
    val storeItemId: Int?,
    val boughtItemId: Long,
)

data class ChatRelationRow(
    val username: String,
    val comment: String?,
    val relation: String?,
)
