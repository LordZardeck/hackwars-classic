package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.ArrayHookValue
import com.hackwars.rewrite.hackscript.BooleanHookValue
import com.hackwars.rewrite.hackscript.FloatHookValue
import com.hackwars.rewrite.hackscript.HookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import java.text.NumberFormat
import kotlinx.serialization.Serializable

class RequestTaskCommand(
    private val stateId: GameStateId,
    private val fileName: String?,
    private val questId: String,
    private val taskName: String,
) : RequestCommand<TaskProgressResponse> {
    override val name: String = "requesttask"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): TaskProgressResponse {
        require(questId.isNotBlank()) { "Quest id is required." }
        require(taskName.isNotBlank()) { "Task name is required." }

        val state = context.requireExistingState(stateId)
        val quest = state.quests.activeQuestsById[questId]
        if (quest == null || state.quests.hasCompletedQuest(questId)) {
            return TaskProgressResponse(
                stateId = stateId,
                questId = questId,
                taskName = taskName,
                progress = quest?.tasksByName?.get(taskName),
                changed = false,
                version = state.version,
            )
        }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                QuestTaskProgressRecordedEvent(
                    questId = questId,
                    taskName = taskName,
                ),
            ),
        )

        return TaskProgressResponse(
            stateId = stateId,
            questId = questId,
            taskName = taskName,
            progress = updated.quests.activeQuestsById[questId]?.tasksByName?.get(taskName),
            changed = true,
            version = updated.version,
        )
    }
}

class RequestSaveCommand(
    private val stateId: GameStateId,
    private val fileName: String,
    private val triggerParameters: Map<String, HookValue>,
) : RequestCommand<SaveFileRequestResponse> {
    override val name: String = "requestsave"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SaveFileRequestResponse {
        val trimmedFileName = fileName.trim()
        require(trimmedFileName.isNotEmpty()) { "Save file name is required." }

        val state = context.requireExistingState(stateId)
        val normalizedValues = normalizeSaveValues(triggerParameters)
        val savePath = buildFilePath("/", "$trimmedFileName.save")
        require(canStoreFileAt(state, savePath)) {
            "State ${stateId.value} does not have enough filesystem capacity for $trimmedFileName.save."
        }

        val saveFile = StoredFile(
            path = savePath,
            name = "$trimmedFileName.save",
            kind = StoredFileKind.SAVE_DATA,
            contents = serializeSaveRows(normalizedValues),
            description = "A save file for $trimmedFileName.",
            quantity = 1,
            maker = trimmedFileName,
            saveMetadata = SaveFileMetadata(valuesByKey = normalizedValues),
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(FileSavedEvent(saveFile)),
        )

        return SaveFileRequestResponse(
            stateId = stateId,
            file = requireNotNull(updated.filesystem.filesByPath[savePath]),
            version = updated.version,
        )
    }
}

class ClueDataCommand(
    private val stateId: GameStateId,
    private val targetIp: String,
    private val data: String?,
) : RequestCommand<ClueDataAcceptedResponse> {
    override val name: String = "cluedata"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): ClueDataAcceptedResponse {
        require(targetIp.isNotBlank()) { "Clue target ip is required." }

        val state = context.requireExistingState(stateId)
        val resolvedData = data.orEmpty()
        if (state.quests.lastClueDataByIp[targetIp] == resolvedData) {
            return ClueDataAcceptedResponse(
                stateId = stateId,
                targetIp = targetIp,
                changed = false,
                version = state.version,
            )
        }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                ClueDataStoredEvent(
                    targetIp = targetIp,
                    data = resolvedData,
                ),
            ),
        )

        return ClueDataAcceptedResponse(
            stateId = stateId,
            targetIp = targetIp,
            changed = true,
            version = updated.version,
        )
    }
}

class MakeBountyCommand(
    private val creatorStateId: GameStateId,
    private val storeStateId: GameStateId,
    private val anonymous: Boolean,
    private val target: String?,
    private val type: Int,
    private val fileName: String?,
    private val folder: String?,
    private val iterations: Int,
    private val reward: Double,
) : RequestCommand<BountyCreatedResponse> {
    override val name: String = "makebounty"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(creatorStateId, storeStateId)

    override suspend fun execute(context: CommandContext): BountyCreatedResponse {
        require(BountyTypes.isSupported(type)) { "Unsupported bounty type $type." }
        require(reward > 0.0) { "Bounty reward must be positive." }
        require(iterations > 0) { "Bounty iterations must be positive." }

        val states = context.loadStates(targetStateIds)
        val creatorState = requireNotNull(states[creatorStateId]) {
            "No creator state exists for ${creatorStateId.value}."
        }
        val storeState = requireNotNull(states[storeStateId]) {
            "No store state exists for ${storeStateId.value}."
        }
        require(creatorState.hasAnyActiveBankingPort()) {
            "Bounty creation requires an active banking port."
        }
        require(creatorState.economy.pettyCash >= reward) {
            "Not enough petty cash to create this bounty."
        }

        val resolvedTarget = target?.takeUnless { it.isBlank() } ?: "*"
        val installSource = if (type == BountyTypes.INSTALL) {
            require(!fileName.isNullOrBlank()) { "Install bounty requires a file name." }
            require(!folder.isNullOrBlank()) { "Install bounty requires a folder." }
            requireNotNull(creatorState.filesystem.resolveFile(folder, fileName)) {
                "Install bounty requires an existing file at ${normalizeDirectoryPath(folder, creatorState.filesystem.currentPath)}/$fileName."
            }
        } else {
            null
        }
        val uniqueName = nextAvailableStoreFileName(
            filesystem = storeState.filesystem,
            baseName = legacyBountyDisplayName(
                creatorStateId = creatorStateId,
                type = type,
                anonymous = anonymous,
            ),
        )
        val bountyMetadata = BountyMetadata(
            type = type,
            target = resolvedTarget,
            iterationsRemaining = iterations,
            reward = reward,
            bountySourceStateId = creatorStateId,
            requiredMaker = installSource?.maker.orEmpty(),
            requiredScriptName = installSource?.name.orEmpty(),
            anonymous = anonymous,
        )
        val bountyFile = StoredFile(
            path = buildFilePath("/Store", uniqueName),
            name = uniqueName,
            kind = StoredFileKind.BOUNTY,
            contents = legacyBountyContents(bountyMetadata),
            description = legacyBountyDescription(
                type = type,
                target = resolvedTarget,
                reward = reward,
                installSource = installSource,
            ),
            quantity = 1,
            maker = creatorStateId.value,
            bountyMetadata = bountyMetadata,
        )

        val updatedStore = context.appendEvents(
            id = storeStateId,
            events = listOf(FileSavedEvent(bountyFile)),
        )
        val updatedCreator = context.appendEvents(
            id = creatorStateId,
            events = listOf(EconomyBalanceAdjustedEvent(pettyCashDelta = -reward)),
        )
        context.evaluatePassivePettyCashChange(
            targetStateId = creatorStateId,
            previousPettyCash = creatorState.economy.pettyCash,
            newPettyCash = updatedCreator.economy.pettyCash,
        )

        return BountyCreatedResponse(
            creatorStateId = creatorStateId,
            storeStateId = storeStateId,
            bountyFile = bountyFile,
            reward = reward,
            creatorVersion = updatedCreator.version,
            storeVersion = updatedStore.version,
        )
    }
}

class RequestTriggerCommand(
    private val stateId: GameStateId,
    private val targetStateId: GameStateId,
    private val selector: TriggerSelector,
    private val sourceIp: String,
    private val triggerParameters: Map<String, HookValue>,
) : RequestCommand<TriggerRequestResponse> {
    override val name: String = "requesttrigger"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId, targetStateId)

    override suspend fun execute(context: CommandContext): TriggerRequestResponse {
        require(sourceIp.isNotBlank()) { "Trigger source ip is required." }

        val execution = context.emitWatchTrigger(
            WatchTriggerIntent(
                targetStateId = targetStateId,
                selector = selector,
                sourceIp = sourceIp,
                parameters = triggerParameters,
                external = targetStateId.value != sourceIp,
                originCommandName = name,
                requestId = context.requestId,
            ),
        )

        return TriggerRequestResponse(
            stateId = stateId,
            targetStateId = targetStateId,
            selector = selector,
            sourceIp = sourceIp,
            accepted = true,
            matchedWatchIndex = execution.matchedWatchIndex,
            executed = execution.executed,
        )
    }
}

class RequestTriggerNoteCommand(
    private val stateId: GameStateId,
    private val targetStateId: GameStateId,
    private val note: String,
    private val sourceIp: String,
    private val triggerParameters: Map<String, HookValue>,
) : RequestCommand<TriggerRequestResponse> {
    override val name: String = "requesttriggernote"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId, targetStateId)

    override suspend fun execute(context: CommandContext): TriggerRequestResponse {
        return RequestTriggerCommand(
            stateId = stateId,
            targetStateId = targetStateId,
            selector = TriggerSelector.ByNote(note),
            sourceIp = sourceIp,
            triggerParameters = triggerParameters,
        ).execute(context)
    }
}

@Serializable
data class RequestTaskPayload(
    val fileName: String? = null,
    val questId: String,
    val taskName: String,
    val targetIp: String? = null,
)

@Serializable
data class RequestSavePayload(
    val fileName: String,
    val triggerParameters: Map<String, HookValue> = emptyMap(),
    val targetIp: String? = null,
)

@Serializable
data class ClueDataPayload(
    val ip: String,
    val data: String? = null,
)

@Serializable
data class MakeBountyPayload(
    val sourceIp: String,
    val anonymous: Boolean? = null,
    val target: String? = null,
    val type: Int? = null,
    val fname: String? = null,
    val folder: String? = null,
    val iterations: Int? = null,
    val reward: Double? = null,
)

@Serializable
data class RequestTriggerPayload(
    val selector: TriggerSelector,
    val triggerParameters: Map<String, HookValue> = emptyMap(),
    val sourceIp: String? = null,
    val targetIp: String,
)

private fun normalizeSaveValues(triggerParameters: Map<String, HookValue>): Map<String, HookValue> {
    return linkedMapOf<String, HookValue>().apply {
        triggerParameters.forEach { (key, value) ->
            val normalizedKey = key.trim()
            require(normalizedKey.isNotEmpty()) { "Save file keys must not be blank." }
            require(value !is ArrayHookValue) {
                "Save files only support scalar values for $normalizedKey."
            }
            put(normalizedKey, value)
        }
    }
}

private fun serializeSaveRows(triggerParameters: Map<String, HookValue>): String {
    return buildString {
        triggerParameters.forEach { (key, value) ->
            append(key)
            append('\t')
            append(value.toLegacySaveType())
            append('\t')
            append(value.toLegacySaveValue())
            append('\n')
        }
    }
}

private fun HookValue.toLegacySaveType(): String = when (this) {
    is StringHookValue -> "string"
    is IntHookValue -> "int"
    is FloatHookValue -> "float"
    is BooleanHookValue -> "bool"
    is ArrayHookValue -> error("Array hook values are not supported for save files.")
}

private fun HookValue.toLegacySaveValue(): String = when (this) {
    is StringHookValue -> value
    is IntHookValue -> value.toString()
    is FloatHookValue -> value.toString()
    is BooleanHookValue -> value.toString()
    is ArrayHookValue -> error("Array hook values are not supported for save files.")
}

private fun ComputerState.hasAnyActiveBankingPort(): Boolean {
    return ports.any { port ->
        port.enabled &&
            !port.dummy &&
            (port.installedApplication?.banking == true || port.installedApplication?.kind == ApplicationKind.BANKING)
    }
}

private fun canStoreFileAt(state: ComputerState, filePath: String): Boolean {
    if (state.filesystem.filesByPath.containsKey(filePath)) {
        return true
    }
    if (state.hardware.hdMaximum <= 0) {
        return true
    }
    return state.filesystem.filesByPath.size < state.hardware.hdMaximum
}

private fun legacyBountyDisplayName(
    creatorStateId: GameStateId,
    type: Int,
    anonymous: Boolean,
): String {
    val typeName = BountyTypes.displayName(type)
    return if (anonymous && type != BountyTypes.CHANGE) {
        "$typeName By (Anonymous)"
    } else {
        "$typeName By (${creatorStateId.value})"
    }
}

private fun legacyBountyDescription(
    type: Int,
    target: String,
    reward: Double,
    installSource: StoredFile?,
): String {
    val rewardText = NumberFormat.getCurrencyInstance().format(reward)
    return buildString {
        append("Bounty Type: ")
        append(BountyTypes.displayName(type))
        append('\n')
        if (target != "*") {
            append("Target: ")
            append(target)
            append('\n')
        } else {
            append("Target: Any Player.\n")
        }
        append("Reward: ")
        append(rewardText)
        append('\n')
        if (type == BountyTypes.INSTALL && installSource != null) {
            append("Must Install: ")
            append(installSource.name)
            append(" Maker: ")
            append(installSource.maker)
            append('\n')
        }
    }
}

private fun legacyBountyContents(metadata: BountyMetadata): String {
    return buildString {
        append("count=").append(metadata.iterationsRemaining).append('\n')
        append("type=").append(metadata.type).append('\n')
        append("reward=").append(metadata.reward).append('\n')
        append("target=").append(metadata.target).append('\n')
        append("bountyip=").append(metadata.bountySourceStateId.value).append('\n')
        append("maker=").append(metadata.requiredMaker).append('\n')
        append("script=").append(metadata.requiredScriptName).append('\n')
    }
}

private fun nextAvailableStoreFileName(
    filesystem: FilesystemState,
    baseName: String,
): String {
    if (!filesystem.filesByPath.containsKey(buildFilePath("/Store", baseName))) {
        return baseName
    }
    var suffix = 2
    while (filesystem.filesByPath.containsKey(buildFilePath("/Store", "$baseName [$suffix]"))) {
        suffix += 1
    }
    return "$baseName [$suffix]"
}
