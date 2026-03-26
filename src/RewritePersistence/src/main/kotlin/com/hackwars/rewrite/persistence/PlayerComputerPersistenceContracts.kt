package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ScriptFamily
import java.time.Instant

data class PersistedPlayerProfile(
    val playerId: String,
    val displayName: String = "",
    val profileImagePath: String = "",
    val profileDescription: String = "",
    val profileLocation: String = "",
    val profilePayload: String = "{}",
)

data class PersistedComputerProjection(
    val computerId: String,
    val playerId: String,
    val ipAddress: String,
    val isNpc: Boolean = false,
    val currentNetworkName: String = "",
    val lastLoginAt: Instant? = null,
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val defaultBankPort: Int? = null,
    val defaultRedirectPort: Int? = null,
    val dailyPayBaseAmount: Double = 1000.0,
    val dailyPayReductionMultiplier: Double = 1.0,
    val dailyPayRevenueTargetStateId: String? = null,
    val dailyPayLastPaidAtEpochMillis: Long = 0L,
    val totalLevel: Int = 0,
    val noobProtectionLevel: Int = 0,
    val projectionPayload: String = "{}",
)

data class PersistedComputerPreference(
    val computerId: String,
    val key: String,
    val value: String,
)

data class PersistedComputerSkillStat(
    val computerId: String,
    val scriptFamily: ScriptFamily,
    val experiencePoints: Double,
)

interface PlayerComputerProjectionRepository {
    suspend fun upsertPlayerProfile(profile: PersistedPlayerProfile)

    suspend fun findPlayerProfile(playerId: String): PersistedPlayerProfile?

    suspend fun upsertComputerProjection(projection: PersistedComputerProjection)

    suspend fun findComputerProjection(computerId: String): PersistedComputerProjection?

    suspend fun replacePreferences(
        computerId: String,
        preferences: Collection<PersistedComputerPreference>,
    )

    suspend fun listPreferences(computerId: String): List<PersistedComputerPreference>

    suspend fun replaceSkillStats(
        computerId: String,
        stats: Collection<PersistedComputerSkillStat>,
    )

    suspend fun listSkillStats(computerId: String): List<PersistedComputerSkillStat>
}
