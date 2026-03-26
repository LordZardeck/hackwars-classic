package com.hackwars.rewrite.persistence

import java.time.Instant

data class PersistedImportBatchSummary(
    val batchId: String,
    val sourceKind: String,
    val sourceLocation: String,
    val payloadType: String,
    val createdAt: Instant,
)

data class RetainedImportReconciliationReport(
    val batchCount: Int,
    val payloadCountsByType: Map<String, Int>,
    val missingPlayerIds: List<String> = emptyList(),
    val missingSessionTickets: List<String> = emptyList(),
    val missingServiceSessions: List<String> = emptyList(),
    val missingComputerIds: List<String> = emptyList(),
    val missingNetworkNames: List<String> = emptyList(),
    val missingWebsiteStateIds: List<String> = emptyList(),
    val missingChatChannelIds: List<String> = emptyList(),
    val missingChatMemberships: List<String> = emptyList(),
    val missingChatMessageIds: List<String> = emptyList(),
    val missingChatRelations: List<String> = emptyList(),
    val missingChatMutes: List<String> = emptyList(),
    val missingChatPresenceConnectionIds: List<String> = emptyList(),
) {
    val mismatchCount: Int =
        missingPlayerIds.size +
            missingSessionTickets.size +
            missingServiceSessions.size +
            missingComputerIds.size +
            missingNetworkNames.size +
            missingWebsiteStateIds.size +
            missingChatChannelIds.size +
            missingChatMemberships.size +
            missingChatMessageIds.size +
            missingChatRelations.size +
            missingChatMutes.size +
            missingChatPresenceConnectionIds.size
}

interface ImportAuditRepository {
    suspend fun listImportBatches(): List<PersistedImportBatchSummary>

    suspend fun buildRetainedReconciliationReport(): RetainedImportReconciliationReport
}
