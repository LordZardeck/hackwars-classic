package com.hackwars.rewrite.persistence

data class PersistedWebsiteProjection(
    val stateId: String,
    val canonicalAddress: String,
    val title: String = "",
    val bodyHtml: String = "",
    val voteCount: Int = 0,
    val votesAvailable: Int = 0,
    val storeRevenueTargetStateId: String? = null,
    val websitePayload: String = "{}",
)

interface WebsiteProjectionRepository {
    suspend fun upsertWebsiteProjection(projection: PersistedWebsiteProjection)

    suspend fun findWebsiteProjection(stateId: String): PersistedWebsiteProjection?

    suspend fun listWebsiteProjections(): List<PersistedWebsiteProjection>
}
