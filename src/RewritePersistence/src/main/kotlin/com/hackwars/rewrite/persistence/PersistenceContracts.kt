package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.NpcCategory
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SnapshotPolicy(
    val eventThreshold: Int = 50,
    val timeThreshold: Duration = 5.seconds,
) {
    fun shouldSnapshot(eventsSinceLastSnapshot: Int, elapsedSinceLastSnapshot: Duration): Boolean {
        return eventsSinceLastSnapshot >= eventThreshold || elapsedSinceLastSnapshot >= timeThreshold
    }
}

data class PersistedEvent(
    val streamId: String,
    val sequence: Long,
    val payload: ByteArray,
    val recordedAt: Instant = Instant.EPOCH,
)

data class PersistedSnapshot(
    val streamId: String,
    val version: Long,
    val payload: ByteArray,
    val recordedAt: Instant = Instant.EPOCH,
)

sealed interface LegacySourceDescriptor {
    val sourceKind: String
    val sourceLocation: String
}

data class LegacyMySqlDumpDescriptor(
    override val sourceLocation: String,
    val databaseName: String,
) : LegacySourceDescriptor {
    override val sourceKind: String = "mysql-dump"
}

data class LegacyXmlDescriptor(
    override val sourceLocation: String,
    val rootElement: String,
) : LegacySourceDescriptor {
    override val sourceKind: String = "xml"
}

data class LegacyJsonDescriptor(
    override val sourceLocation: String,
    val documentType: String,
) : LegacySourceDescriptor {
    override val sourceKind: String = "json"
}

data class RewriteSeedBatch(
    val batchId: String,
    val source: LegacySourceDescriptor,
    val seedPayload: SeedPayload,
    val createdAt: Instant,
)

sealed interface SeedPayload

enum class PersistedServiceKind {
    GAME,
    CHAT,
}

data class PersistedSessionTicket(
    val sessionTicket: String,
    val playerId: String,
    val playFabId: String,
    val playerIp: String,
    val issuedAt: Instant = Instant.EPOCH,
    val expiresAt: Instant? = null,
    val ticketPayload: String = "{}",
)

data class PersistedServiceSession(
    val serviceSessionId: String,
    val serviceKind: PersistedServiceKind,
    val connectionId: String,
    val playerId: String,
    val playFabId: String,
    val playerIp: String,
    val sessionTicket: String,
    val clientBuild: String = "",
    val heartbeatIntervalMillis: Long = 0L,
    val authenticatedAt: Instant = Instant.EPOCH,
    val lastSeenAt: Instant = authenticatedAt,
    val closedAt: Instant? = null,
    val sessionPayload: String = "{}",
)

interface AuthSessionRepository {
    suspend fun upsertSessionTicket(ticket: PersistedSessionTicket)

    suspend fun findSessionTicket(sessionTicket: String): PersistedSessionTicket?

    suspend fun upsertServiceSession(session: PersistedServiceSession)

    suspend fun findServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
    ): PersistedServiceSession?

    suspend fun listActiveServiceSessions(playerId: String): List<PersistedServiceSession>

    suspend fun touchServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        lastSeenAt: Instant,
    )

    suspend fun closeServiceSession(
        serviceKind: PersistedServiceKind,
        connectionId: String,
        closedAt: Instant,
    )
}

data class SeedPlayerAccount(
    val playerId: String,
    val playFabId: String,
    val playerIp: String,
) : SeedPayload

data class SeedComputerState(
    val computerId: String,
    val playerId: String,
    val ipAddress: String,
    val isNpc: Boolean = false,
) : SeedPayload

data class SeedInventorySnapshot(
    val computerId: String,
    val notes: List<String>,
    val websiteTitle: String = "",
    val websiteBody: String = "",
    val lastLoginAtEpochMillis: Long? = null,
    val votesAvailable: Int = 0,
    val voteCount: Int = 0,
    val totalLevel: Int = 0,
    val noobProtectionLevel: Int = 0,
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val commodities: List<Double> = List(5) { 0.0 },
    val commodityRespawn: List<Double> = commodities,
    val currentNetworkName: String = "UGOPNet",
    val allowedNetworks: List<String> = emptyList(),
    val lastNetworkSwitchAtEpochMillis: Long = 0L,
    val scanningExperience: Double = 0.0,
    val firewallExperience: Double = 0.0,
    val currentCpuLoad: Double = 0.0,
    val cpuMax: Double = 100.0,
    val memoryType: Int = 0,
    val watchCapacityBoost: Int = 0,
    val freezeImmune: Boolean = false,
    val destroyWatchesImmune: Boolean = false,
    val activeQuestLabelsById: Map<String, String> = emptyMap(),
    val seedSaveFileName: String? = null,
    val enableBanking: Boolean = true,
    val enableFtp: Boolean = true,
    val enableHttp: Boolean = true,
    val enableWatchBinary: Boolean = false,
    val seedInstalledWatchCount: Int = 0,
    val seedEnabledWatchCount: Int = 0,
    val seedWatchCpuCost: Double = 5.0,
) : SeedPayload

data class SeedWorldDirectory(
    val networks: List<SeedWorldNetworkDefinition>,
) : SeedPayload

data class SeedWorldNetworkDefinition(
    val name: String,
    val storeStateId: String? = null,
    val attachedNetworks: List<SeedAttachedNetworkLink> = emptyList(),
    val npcs: List<SeedWorldNpcEntry> = emptyList(),
)

data class SeedAttachedNetworkLink(
    val targetNetworkName: String,
    val entranceMessage: String,
    val failureMessage: String = entranceMessage,
)

data class SeedWorldNpcEntry(
    val stateId: String,
    val displayName: String,
    val title: String = "",
    val category: NpcCategory,
    val commodity: String? = null,
)

interface RewriteSeedPlanner {
    fun plan(source: LegacySourceDescriptor): RewriteSeedBatch
}

class DefaultRewriteSeedPlanner : RewriteSeedPlanner {
    override fun plan(source: LegacySourceDescriptor): RewriteSeedBatch {
        val seedPayload: SeedPayload = when (source) {
            is LegacyMySqlDumpDescriptor -> SeedPlayerAccount(
                playerId = source.databaseName.lowercase(),
                playFabId = "PF-${source.databaseName.uppercase()}",
                playerIp = "0.0.0.0",
            )
            is LegacyXmlDescriptor -> SeedComputerState(
                computerId = source.rootElement.lowercase(),
                playerId = source.rootElement.lowercase(),
                ipAddress = "0.0.0.0",
            )
            is LegacyJsonDescriptor -> SeedInventorySnapshot(
                computerId = source.documentType.lowercase(),
                notes = listOf(source.documentType),
            )
        }

        return RewriteSeedBatch(
            batchId = "${source.sourceKind}:${source.sourceLocation}",
            source = source,
            seedPayload = seedPayload,
            createdAt = Instant.EPOCH,
        )
    }
}

interface RewriteSeedSink {
    suspend fun write(batch: RewriteSeedBatch)
}

class LegacyImportCoordinator(
    private val planner: RewriteSeedPlanner = DefaultRewriteSeedPlanner(),
) {
    fun plan(source: LegacySourceDescriptor): RewriteSeedBatch {
        return planner.plan(source)
    }
}

class SeedBatchRecorder : RewriteSeedSink {
    private val writtenBatches = mutableListOf<RewriteSeedBatch>()

    override suspend fun write(batch: RewriteSeedBatch) {
        writtenBatches += batch
    }

    fun snapshot(): List<RewriteSeedBatch> = writtenBatches.toList()
}
