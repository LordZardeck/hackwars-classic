package com.hackwars.data.model

data class ForumLoginSnapshot(
    val ip: String,
    val npc: String,
    val daysSinceLastLogin: Int?,
)

data class ForumActivity(
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

data class DropItemData(
    val weight: Int,
    val data: String,
)

data class PendingPurchase(
    val storeItemId: Int?,
    val boughtItemId: Long,
)

data class NetworkDefinition(
    val id: Int,
    val name: String,
    val attackProbability: Float,
    val attachedNetworks: List<AttachedNetworkLink>,
    val storeNpcs: List<NetworkNpcView>,
    val miningNpcs: List<NetworkNpcView>,
    val attackNpcs: List<NetworkNpcView>,
    val questNpcs: List<NetworkNpcView>,
) {
    val storeNpcIp: String
        get() = storeNpcs.firstOrNull()?.ip.orEmpty()
}

data class SearchBootstrapRow(
    val statsXml: String,
    val ip: String,
    val daysSinceLastLogin: Int?,
    val npc: String?,
)

data class ChatRelationRow(
    val username: String,
    val comment: String?,
    val relation: String?,
)
