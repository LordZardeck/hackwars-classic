package com.hackwars.rewrite.persistence

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
    val votesAvailable: Int = 0,
    val voteCount: Int = 0,
    val totalLevel: Int = 0,
    val noobProtectionLevel: Int = 0,
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val currentNetworkName: String = "UGOPNet",
    val allowedNetworks: List<String> = emptyList(),
    val lastNetworkSwitchAtEpochMillis: Long = 0L,
    val scanningExperience: Int = 0,
    val firewallExperience: Int = 0,
    val currentCpuLoad: Double = 0.0,
    val activeQuestLabelsById: Map<String, String> = emptyMap(),
    val seedSaveFileName: String? = null,
    val enableBanking: Boolean = true,
    val enableFtp: Boolean = true,
    val enableHttp: Boolean = true,
) : SeedPayload

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
