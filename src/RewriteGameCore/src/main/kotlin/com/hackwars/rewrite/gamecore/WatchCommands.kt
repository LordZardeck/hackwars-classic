package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MAX_INSTALLED_WATCHES: Int = 21
private val MAX_ACTIVE_WATCHES_BY_MEMORY_TYPE: List<Int> = listOf(4, 6, 8, 12, 5)

class FetchWatchesCommand(
    private val stateId: GameStateId,
) : RequestCommand<WatchListResponse> {
    override val name: String = "fetchwatches"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchListResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(id = stateId, playerIp = stateId.value)
        return state.toWatchListResponse()
    }
}

class InstallWatchCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
    private val typeCode: Int,
    private val portNumber: Int,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "installwatch"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        if (!state.hasPortNumber(portNumber)) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.PORT_NOT_FOUND,
                message = "Port $portNumber does not exist on ${stateId.value}.",
            )
        }
        if (state.watches.watches.size >= MAX_INSTALLED_WATCHES) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.INSTALLED_LIMIT_REACHED,
                message = "You can only have $MAX_INSTALLED_WATCHES watches installed.",
            )
        }

        val source = state.filesystem.resolveFile(path, fileName)
            ?: return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.MISSING_FILE,
                message = "No compiled watch file found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName.",
            )
        if (source.kind != StoredFileKind.APPLICATION_BINARY || source.compiledBinary?.scriptFamily != ScriptFamily.WATCH) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.INVALID_FILE_TYPE,
                message = "Only compiled watch binaries can be installed.",
            )
        }
        val watchKind = WatchKind.fromLegacyCode(typeCode)
            ?: return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.INVALID_WATCH_KIND,
                message = "Unsupported watch type $typeCode.",
            )
        if (state.runtime.currentCpuLoad + source.cpuCost > state.hardware.cpuMax) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.CPU_HEADROOM_EXCEEDED,
                message = "Installing ${source.name} would exceed the current CPU limit.",
            )
        }

        val installedWatch = InstalledWatch(
            kind = watchKind,
            enabled = false,
            note = source.name,
            cpuCost = source.cpuCost,
            quantityThreshold = 0.0,
            baselineQuantity = when (watchKind) {
                WatchKind.PETTY_CASH -> state.economy.pettyCash
                WatchKind.HEALTH -> 100.0
                WatchKind.SCAN -> 0.0
            },
            installPort = portNumber,
            searchFirewallType = 0,
            observedPorts = emptyList(),
            contents = source.contents,
            scriptBundle = source.scriptBundle,
            compiledBinary = source.compiledBinary,
        )

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                WatchInstalledEvent(
                    sourceFilePath = source.path,
                    remainingSourceFile = source.decrementQuantity(),
                    installedWatch = installedWatch,
                ),
            ),
        )

        return WatchMutationResponse(
            stateId = stateId,
            operation = name,
            accepted = true,
            message = "watch-installed",
            affectedWatchIndex = updated.watches.watches.lastIndex,
            snapshot = updated.toWatchListResponse(),
        )
    }
}

class SetWatchNoteCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val note: String,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "setwatchnote"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf("watches.watches.$watchIndex.note"),
            message = "watch-note-updated",
        ) { watch, _ ->
            watch.copy(note = note)
        }
    }
}

class SetWatchOnOffCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val enabled: Boolean,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "setwatchonoff"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        val watch = state.watchAt(watchIndex)
            ?: return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.WATCH_NOT_FOUND,
                message = "No watch exists at index $watchIndex.",
                affectedWatchIndex = watchIndex,
            )

        if (!enabled && state.isCurrentlyOverheated(System.currentTimeMillis())) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.OVERHEATED,
                message = "You cannot disable watches while overheated.",
                affectedWatchIndex = watchIndex,
            )
        }
        if (watch.enabled == enabled) {
            return WatchMutationResponse(
                stateId = stateId,
                operation = name,
                accepted = true,
                message = if (enabled) "watch-already-enabled" else "watch-already-disabled",
                affectedWatchIndex = watchIndex,
                snapshot = state.toWatchListResponse(),
            )
        }
        if (enabled && state.activeWatchCount() >= state.maximumActiveWatchCount()) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.ACTIVE_LIMIT_REACHED,
                message = "You cannot have more watches running at once.",
                affectedWatchIndex = watchIndex,
            )
        }

        val nextCpuLoad = if (enabled) {
            state.runtime.currentCpuLoad + watch.cpuCost
        } else {
            (state.runtime.currentCpuLoad - watch.cpuCost).coerceAtLeast(0.0)
        }
        if (enabled && nextCpuLoad > state.hardware.cpuMax) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.CPU_HEADROOM_EXCEEDED,
                message = "Enabling this watch would exceed the current CPU limit.",
                affectedWatchIndex = watchIndex,
            )
        }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                WatchManagerUpdatedEvent(
                    changedPathList = setOf(
                        "watches.watches.$watchIndex.enabled",
                        "runtime.currentCpuLoad",
                    ),
                    deltaKeyList = setOf("watches", "runtime"),
                    watches = state.watches.copy(
                        watches = state.watches.watches.replaceAt(
                            watchIndex,
                            watch.copy(enabled = enabled),
                        ),
                    ),
                    currentCpuLoad = nextCpuLoad,
                    includeRuntime = true,
                ),
            ),
        )

        return WatchMutationResponse(
            stateId = stateId,
            operation = name,
            accepted = true,
            message = if (enabled) "watch-enabled" else "watch-disabled",
            affectedWatchIndex = watchIndex,
            snapshot = updated.toWatchListResponse(),
        )
    }
}

class SetWatchQuantityCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val quantity: Double,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "setwatchquantity"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf("watches.watches.$watchIndex.quantityThreshold"),
            message = "watch-quantity-updated",
        ) { watch, _ ->
            watch.copy(quantityThreshold = quantity)
        }
    }
}

class SetWatchObservedPortsCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val observedPorts: List<Int>,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "setwatchobservedports"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        if (observedPorts.any { !state.hasPortNumber(it) }) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.INVALID_OBSERVED_PORTS,
                message = "Observed ports must belong to the host state.",
                affectedWatchIndex = watchIndex,
            )
        }
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf("watches.watches.$watchIndex.observedPorts"),
            message = "watch-observed-ports-updated",
        ) { watch, _ ->
            watch.copy(observedPorts = observedPorts)
        }
    }
}

class SetWatchSearchFirewallCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val searchFirewallType: Int,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "setwatchsearchfirewall"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf("watches.watches.$watchIndex.searchFirewallType"),
            message = "watch-search-firewall-updated",
        ) { watch, _ ->
            watch.copy(searchFirewallType = searchFirewallType)
        }
    }
}

class ChangeWatchPortCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val portNumber: Int,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "changewatchport"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        if (!state.hasPortNumber(portNumber)) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.PORT_NOT_FOUND,
                message = "Port $portNumber does not exist on ${stateId.value}.",
                affectedWatchIndex = watchIndex,
            )
        }
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf("watches.watches.$watchIndex.installPort"),
            message = "watch-port-updated",
        ) { watch, _ ->
            watch.copy(installPort = portNumber)
        }
    }
}

class ChangeWatchTypeCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
    private val typeCode: Int,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "changewatchtype"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        val watchKind = WatchKind.fromLegacyCode(typeCode)
            ?: return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.INVALID_WATCH_KIND,
                message = "Unsupported watch type $typeCode.",
                affectedWatchIndex = watchIndex,
            )
        return updateWatch(
            context = context,
            stateId = stateId,
            operation = name,
            watchIndex = watchIndex,
            changedPaths = setOf(
                "watches.watches.$watchIndex.kind",
                "watches.watches.$watchIndex.baselineQuantity",
            ),
            message = "watch-type-updated",
        ) { watch, currentState ->
            watch.copy(
                kind = watchKind,
                baselineQuantity = when (watchKind) {
                    WatchKind.PETTY_CASH -> currentState.economy.pettyCash
                    WatchKind.HEALTH -> 100.0
                    WatchKind.SCAN -> 0.0
                },
            )
        }
    }
}

class DeleteWatchCommand(
    private val stateId: GameStateId,
    private val watchIndex: Int,
) : RequestCommand<WatchMutationResponse> {
    override val name: String = "deletewatch"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): WatchMutationResponse {
        val state = context.requireExistingState(stateId)
        val watch = state.watchAt(watchIndex)
            ?: return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.WATCH_NOT_FOUND,
                message = "No watch exists at index $watchIndex.",
                affectedWatchIndex = watchIndex,
            )
        if (state.isCurrentlyOverheated(System.currentTimeMillis())) {
            return state.watchFailure(
                operation = name,
                code = WatchMutationFailureCode.OVERHEATED,
                message = "You cannot delete watches while overheated.",
                affectedWatchIndex = watchIndex,
            )
        }

        val includeRuntime = watch.enabled
        val nextCpuLoad = if (includeRuntime) {
            (state.runtime.currentCpuLoad - watch.cpuCost).coerceAtLeast(0.0)
        } else {
            state.runtime.currentCpuLoad
        }
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                WatchManagerUpdatedEvent(
                    changedPathList = buildSet {
                        add("watches.watches")
                        if (includeRuntime) {
                            add("runtime.currentCpuLoad")
                        }
                    },
                    deltaKeyList = buildSet {
                        add("watches")
                        if (includeRuntime) {
                            add("runtime")
                        }
                    },
                    watches = state.watches.copy(
                        watches = state.watches.watches.filterIndexed { index, _ -> index != watchIndex },
                    ),
                    currentCpuLoad = nextCpuLoad,
                    includeRuntime = includeRuntime,
                ),
            ),
        )

        return WatchMutationResponse(
            stateId = stateId,
            operation = name,
            accepted = true,
            message = "watch-deleted",
            affectedWatchIndex = watchIndex,
            snapshot = updated.toWatchListResponse(),
        )
    }
}

@Serializable
data class FetchWatchesPayload(
    val ip: String,
)

@Serializable
data class InstallWatchPayload(
    val ip: String,
    val path: String? = null,
    val name: String? = null,
    val type: Int? = null,
    val port: Int? = null,
)

@Serializable
data class SetWatchNotePayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val note: String? = null,
)

@Serializable
data class SetWatchOnOffPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val state: Boolean? = null,
)

@Serializable
data class SetWatchQuantityPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val quantity: Double? = null,
)

@Serializable
data class SetWatchObservedPortsPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    val observedPorts: List<Int> = emptyList(),
)

@Serializable
data class SetWatchSearchFirewallPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    @SerialName("searchFireWall")
    val searchFirewall: Int? = null,
)

@Serializable
data class ChangeWatchPortPayload(
    val ip: String,
    @SerialName("watchId")
    val watchId: Int? = null,
    @SerialName("portId")
    val portId: Int? = null,
)

@Serializable
data class ChangeWatchTypePayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
    @SerialName("portID")
    val newType: Int? = null,
)

@Serializable
data class DeleteWatchPayload(
    val ip: String,
    @SerialName("watchID")
    val watchId: Int? = null,
)

private suspend fun updateWatch(
    context: CommandContext,
    stateId: GameStateId,
    operation: String,
    watchIndex: Int,
    changedPaths: Set<String>,
    message: String,
    update: (InstalledWatch, ComputerState) -> InstalledWatch,
): WatchMutationResponse {
    val state = context.requireExistingState(stateId)
    val watch = state.watchAt(watchIndex)
        ?: return state.watchFailure(
            operation = operation,
            code = WatchMutationFailureCode.WATCH_NOT_FOUND,
            message = "No watch exists at index $watchIndex.",
            affectedWatchIndex = watchIndex,
        )

    val updated = context.appendEvents(
        id = state.id,
        events = listOf(
            WatchManagerUpdatedEvent(
                changedPathList = changedPaths,
                deltaKeyList = setOf("watches"),
                watches = state.watches.copy(
                    watches = state.watches.watches.replaceAt(
                        watchIndex,
                        update(watch, state),
                    ),
                ),
                currentCpuLoad = state.runtime.currentCpuLoad,
                includeRuntime = false,
            ),
        ),
    )

    return WatchMutationResponse(
        stateId = state.id,
        operation = operation,
        accepted = true,
        message = message,
        affectedWatchIndex = watchIndex,
        snapshot = updated.toWatchListResponse(),
    )
}

private fun ComputerState.toWatchListResponse(): WatchListResponse {
    return WatchListResponse(
        stateId = id,
        watches = watches.watches,
        installedCount = watches.watches.size,
        maximumInstalledCount = MAX_INSTALLED_WATCHES,
        activeCount = activeWatchCount(),
        maximumActiveCount = maximumActiveWatchCount(),
        currentCpuLoad = runtime.currentCpuLoad,
        maximumCpuLoad = hardware.cpuMax,
    )
}

private fun ComputerState.watchFailure(
    operation: String,
    code: WatchMutationFailureCode,
    message: String,
    affectedWatchIndex: Int? = null,
): WatchMutationResponse {
    return WatchMutationResponse(
        stateId = id,
        operation = operation,
        accepted = false,
        failureCode = code,
        message = message,
        affectedWatchIndex = affectedWatchIndex,
        snapshot = toWatchListResponse(),
    )
}

private fun ComputerState.watchAt(index: Int): InstalledWatch? = watches.watches.getOrNull(index)

private fun ComputerState.activeWatchCount(): Int = watches.watches.count { it.enabled }

private fun ComputerState.maximumActiveWatchCount(): Int {
    val base = MAX_ACTIVE_WATCHES_BY_MEMORY_TYPE.getOrElse(hardware.memoryType) { 0 }
    val bonus = hardware.equipmentSlots.values.sumOf { it.watchCapacityBoost }
    return (base + bonus).coerceAtLeast(0)
}

private fun ComputerState.hasPortNumber(portNumber: Int): Boolean = ports.any { it.number == portNumber }

private fun StoredFile.decrementQuantity(): StoredFile? {
    return if (quantity <= 1) {
        null
    } else {
        copy(quantity = quantity - 1)
    }
}

private fun List<InstalledWatch>.replaceAt(index: Int, watch: InstalledWatch): List<InstalledWatch> {
    return mapIndexed { currentIndex, currentWatch ->
        if (currentIndex == index) {
            watch
        } else {
            currentWatch
        }
    }
}
