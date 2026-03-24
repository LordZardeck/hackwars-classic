package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
@JvmInline
value class GameStateId(val value: String)

@Serializable
data class ComputerState(
    val id: GameStateId,
    val version: Long = 0,
    val identity: ComputerIdentity = ComputerIdentity(),
    val economy: EconomyState = EconomyState(),
    val hardware: HardwareState = HardwareState(),
    val ports: List<PortState> = emptyList(),
    val filesystem: FilesystemState = FilesystemState(),
    val website: WebsiteState = WebsiteState(),
    val combat: CombatState = CombatState(),
    val quests: QuestState = QuestState(),
    val preferences: PreferenceState = PreferenceState(),
    val runtime: RuntimeState = RuntimeState(),
) {
    companion object {
        fun empty(
            id: GameStateId,
            playFabId: String = "",
            playerIp: String = id.value,
            displayName: String = "",
        ): ComputerState = ComputerState(
            id = id,
            identity = ComputerIdentity(
                playFabId = playFabId,
                playerIp = playerIp,
                displayName = displayName,
            ),
        )
    }
}

@Serializable
data class ComputerIdentity(
    val playFabId: String = "",
    val playerIp: String = "",
    val displayName: String = "",
)

@Serializable
data class EconomyState(
    val pettyCash: Double = 0.0,
    val bankMoney: Double = 0.0,
    val commodities: List<Double> = List(5) { 0.0 },
)

@Serializable
data class HardwareState(
    val cpuType: Int = 0,
    val cpuMax: Double = 0.0,
    val memoryType: Int = 0,
    val hdType: Int = 0,
    val hdQuantity: Int = 0,
    val hdMaximum: Int = 0,
)

@Serializable
data class PortState(
    val number: Int,
    val type: String = "",
    val enabled: Boolean = true,
    val defaultPort: Boolean = false,
    val dummy: Boolean = false,
)

@Serializable
data class FilesystemState(
    val currentPath: String = "/",
    val files: List<FileEntry> = emptyList(),
    val directories: List<String> = emptyList(),
)

@Serializable
data class FileEntry(
    val path: String,
    val name: String,
    val contents: String = "",
    val description: String = "",
)

@Serializable
data class WebsiteState(
    val title: String = "",
    val body: String = "",
)

@Serializable
data class CombatState(
    val activePrograms: List<String> = emptyList(),
    val healthByPort: Map<Int, Int> = emptyMap(),
)

@Serializable
data class QuestState(
    val activeQuestIds: List<String> = emptyList(),
    val completedQuestIds: List<String> = emptyList(),
)

@Serializable
data class PreferenceState(
    val values: Map<String, String> = emptyMap(),
)

@Serializable
data class RuntimeState(
    val countdownSeconds: Int = 0,
    val lastMutationVersion: Long = 0,
)

@Serializable
sealed interface ComputerEvent {
    val changedPaths: Set<String>
    val deltaKeys: Set<String>

    fun applyTo(state: ComputerState, nextVersion: Long): ComputerState

    fun toProjection(state: ComputerState): DeltaProjection
}

@Serializable
@SerialName("preference_set")
data class PreferenceSetEvent(
    val key: String,
    val value: String,
) : ComputerEvent {
    override val changedPaths: Set<String> = setOf("preferences.values.$key")
    override val deltaKeys: Set<String> = setOf("preferences")

    override fun applyTo(state: ComputerState, nextVersion: Long): ComputerState {
        return state.copy(
            version = nextVersion,
            preferences = state.preferences.copy(
                values = state.preferences.values + (key to value),
            ),
            runtime = state.runtime.copy(
                lastMutationVersion = nextVersion,
            ),
        )
    }

    override fun toProjection(state: ComputerState): DeltaProjection {
        return PreferenceDeltaProjection(
            values = state.preferences.values,
        )
    }
}

@Serializable
sealed interface DeltaProjection

@Serializable
@SerialName("preferences")
data class PreferenceDeltaProjection(
    val values: Map<String, String>,
) : DeltaProjection

@Serializable
@SerialName("state_summary")
data class StateSummaryDeltaProjection(
    val version: Long,
    val playerIp: String,
) : DeltaProjection

@Serializable
data class ComputerDelta(
    val gameStateId: GameStateId,
    val sequence: Long,
    val changedPaths: Set<String>,
    val deltaKeys: Set<String>,
    val projection: DeltaProjection,
)

enum class ProgramLifecycleStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Serializable
data class ProgramProgress(
    val message: String = "",
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
)

@Serializable
data class ProgramUpdate(
    val programId: String,
    val programType: String,
    val status: ProgramLifecycleStatus,
    val relatedStateIds: Set<GameStateId>,
    val progress: ProgramProgress = ProgramProgress(),
)

@Serializable
data class GameSessionBootstrapResult(
    val state: ComputerState,
)

@Serializable
data class ScanResponse(
    val targetIp: String,
    val openPorts: List<Int>,
)

@Serializable
data class SetPreferencePayload(
    val key: String,
    val value: String,
)

@Serializable
data class SetPreferenceCommandResponse(
    val key: String,
    val value: String,
    val version: Long,
)

data class CommandLifetime(val timeout: Duration) {
    companion object {
        val defaultFireAndForget: CommandLifetime = CommandLifetime(5.seconds)
        val defaultRequest: CommandLifetime = CommandLifetime(30.seconds)
        val defaultProgram: CommandLifetime = CommandLifetime(60.seconds)
    }
}

fun ComputerState.applyEvents(events: List<ComputerEvent>): ComputerState {
    var state = this
    events.forEach { event ->
        state = event.applyTo(state, state.version + 1)
    }
    return state
}

fun buildComputerDelta(
    stateId: GameStateId,
    updatedState: ComputerState,
    events: List<ComputerEvent>,
): ComputerDelta {
    val changedPaths = events.flatMapTo(linkedSetOf()) { it.changedPaths }
    val deltaKeys = events.flatMapTo(linkedSetOf()) { it.deltaKeys }
    val projection = when {
        events.isEmpty() -> StateSummaryDeltaProjection(
            version = updatedState.version,
            playerIp = updatedState.identity.playerIp,
        )

        events.distinctBy { it::class }.size == 1 -> events.last().toProjection(updatedState)

        else -> StateSummaryDeltaProjection(
            version = updatedState.version,
            playerIp = updatedState.identity.playerIp,
        )
    }

    return ComputerDelta(
        gameStateId = stateId,
        sequence = updatedState.version,
        changedPaths = changedPaths,
        deltaKeys = deltaKeys,
        projection = projection,
    )
}
