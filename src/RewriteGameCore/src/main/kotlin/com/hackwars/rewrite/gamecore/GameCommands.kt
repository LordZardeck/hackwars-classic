package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

class GameSessionBootstrapCommand(
    private val stateId: GameStateId,
    private val playFabId: String,
    private val interestRegistry: InterestRegistry,
) : RequestCommand<GameSessionBootstrapResult> {
    override val name: String = "game-session-bootstrap"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): GameSessionBootstrapResult {
        val connectionId = checkNotNull(context.connectionId) {
            "GameSessionBootstrapCommand requires a connection id."
        }
        interestRegistry.register(connectionId, stateId)
        val state = context.loadState(stateId)
            ?: ComputerState.empty(
                id = stateId,
                playFabId = playFabId,
                playerIp = stateId.value,
            )
        return GameSessionBootstrapResult(state = state)
    }
}

class ScanCommand(
    private val targetStateId: GameStateId,
) : RequestCommand<ScanResponse> {
    override val name: String = "requestscan"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(targetStateId)

    override suspend fun execute(context: CommandContext): ScanResponse {
        val state = context.loadState(targetStateId)
            ?: ComputerState.empty(targetStateId, playerIp = targetStateId.value)
        return ScanResponse(
            targetIp = targetStateId.value,
            openPorts = state.ports.filter { it.enabled }.map { it.number }.sorted(),
        )
    }
}

class SetPreferenceCommand(
    private val stateId: GameStateId,
    private val key: String,
    private val value: String,
) : RequestCommand<SetPreferenceCommandResponse> {
    override val name: String = "setpreferences"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SetPreferenceCommandResponse {
        val updatedState = context.appendEvents(
            id = stateId,
            events = listOf(
                PreferenceSetEvent(
                    key = key,
                    value = value,
                ),
            ),
        )
        return SetPreferenceCommandResponse(
            key = key,
            value = value,
            version = updatedState.version,
        )
    }
}

class RequestDirectoryCommand(
    private val stateId: GameStateId,
    private val path: String?,
) : RequestCommand<DirectoryListingResponse> {
    override val name: String = "requestdirectory"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): DirectoryListingResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val listing = state.filesystem.listDirectory(path)
        return DirectoryListingResponse(
            stateId = stateId,
            path = listing.path,
            directories = listing.directories,
            files = listing.files,
            version = state.version,
        )
    }
}

class RequestSecondaryDirectoryCommand(
    private val stateId: GameStateId,
    private val targetStateId: GameStateId,
    private val path: String?,
    private val portNumber: Int,
) : RequestCommand<SecondaryDirectoryListingResponse> {
    override val name: String = "requestsecondarydirectory"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId, targetStateId)

    override suspend fun execute(context: CommandContext): SecondaryDirectoryListingResponse {
        val states = context.loadStates(targetStateIds)
        val requesterState = states[stateId] ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val targetState = states[targetStateId] ?: ComputerState.empty(targetStateId, playerIp = targetStateId.value)
        val normalizedPath = normalizeDirectoryPath(path, requesterState.filesystem.currentPath)
        val listing = targetState.filesystem.listDirectory(normalizedPath)
        return SecondaryDirectoryListingResponse(
            requesterStateId = stateId,
            targetStateId = targetStateId,
            portNumber = portNumber,
            path = listing.path,
            directories = listing.directories,
            files = listing.files,
            version = targetState.version,
        )
    }
}

class RequestFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
) : RequestCommand<FileContentsResponse> {
    override val name: String = "requestfile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): FileContentsResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        return FileContentsResponse(
            stateId = stateId,
            file = state.filesystem.resolveFile(path, fileName)?.copy(),
            version = state.version,
        )
    }
}

class CreateFolderCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val folderName: String,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "createfolder"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val parentPath = normalizeDirectoryPath(path, state.filesystem.currentPath)
        val directoryPath = normalizeDirectoryPath("$parentPath/$folderName", parentPath)
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                DirectoryCreatedEvent(
                    directory = DirectoryEntry(
                        path = directoryPath,
                        name = directoryPath.substringAfterLast('/').ifBlank { "/" },
                    ),
                ),
            ),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "folder-created",
        )
    }
}

class DeleteFolderCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val folderName: String,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "deletefolder"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val parentPath = normalizeDirectoryPath(path, state.filesystem.currentPath)
        val directoryPath = normalizeDirectoryPath("$parentPath/$folderName", parentPath)
        if (!state.filesystem.directoriesByPath.containsKey(directoryPath)) {
            return MutationAcceptedResponse(
                stateId = stateId,
                version = state.version,
                message = "folder-missing",
            )
        }
        val removedDirectories = state.filesystem.directoriesByPath.keys
            .filter { it == directoryPath || it.startsWith("$directoryPath/") }
            .toSet()
        val removedFiles = state.filesystem.filesByPath.keys
            .filter { it.startsWith("$directoryPath/") }
            .toSet()
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                DirectoryDeletedEvent(
                    directoryPath = directoryPath,
                    removedDirectoryPaths = removedDirectories,
                    removedFilePaths = removedFiles,
                ),
            ),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "folder-deleted",
        )
    }
}

class DeleteFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "deletefile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val filePath = buildFilePath(normalizeDirectoryPath(path, state.filesystem.currentPath), fileName)
        if (!state.filesystem.filesByPath.containsKey(filePath)) {
            return MutationAcceptedResponse(
                stateId = stateId,
                version = state.version,
                message = "file-missing",
            )
        }
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(FileDeletedEvent(filePath = filePath)),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "file-deleted",
        )
    }
}

class DeleteMultiCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileNames: List<String>,
    private val directoryNames: List<String>,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "deletemulti"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val normalizedPath = normalizeDirectoryPath(path, state.filesystem.currentPath)
        val filePaths = fileNames
            .map { buildFilePath(normalizedPath, it) }
            .filter { state.filesystem.filesByPath.containsKey(it) }
            .toSet()
        val directoryPaths = directoryNames
            .map { normalizeDirectoryPath("$normalizedPath/$it", normalizedPath) }
            .filter { state.filesystem.directoriesByPath.containsKey(it) }
            .toSet()
        if (filePaths.isEmpty() && directoryPaths.isEmpty()) {
            return MutationAcceptedResponse(
                stateId = stateId,
                version = state.version,
                message = "delete-empty",
            )
        }
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                FilesDeletedEvent(
                    filePaths = filePaths,
                    directoryPaths = directoryPaths,
                ),
            ),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "files-deleted",
        )
    }
}

class SaveFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val file: StoredFile,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "savefile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        val normalizedPath = normalizeDirectoryPath(path, state.filesystem.currentPath)
        val normalizedFile = file.copy(
            path = buildFilePath(normalizedPath, file.name),
            quantity = file.quantity.coerceAtLeast(1),
        ).validateScriptBundle()
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(FileSavedEvent(normalizedFile)),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "file-saved",
        )
    }
}

class CompileFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
) : RequestCommand<CompileFileResponse> {
    override val name: String = "compilefile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): CompileFileResponse {
        val state = context.requireExistingState(stateId)
        val source = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No source file found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        require(source.kind == StoredFileKind.SCRIPT_SOURCE) {
            "Only script source files can be compiled."
        }
        source.validateScriptBundle()
        val binaryMetadata = requireNotNull(source.compiledBinary) {
            "Compile metadata is required for ${source.name}."
        }
        require(state.economy.pettyCash >= source.compileCost) {
            "Not enough petty cash to compile ${source.name}."
        }
        val targetDirectory = parentDirectoryOf(source.path)
        val compiledName = binaryMetadata.outputName.ifBlank {
            defaultCompiledName(source.name, binaryMetadata)
        }
        val compiledPath = buildFilePath(targetDirectory, compiledName)
        val existingCompiled = state.filesystem.filesByPath[compiledPath]
        val compiledFile = source.toCompiledBinary(
            path = compiledPath,
            name = compiledName,
            quantity = (existingCompiled?.quantity ?: 0) + 1,
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                FileCompiledEvent(
                    sourceFilePath = source.path,
                    remainingSourceFile = source,
                    compiledFile = compiledFile,
                    pettyCashDelta = -source.compileCost,
                    scriptFamily = binaryMetadata.scriptFamily,
                    experienceDelta = binaryMetadata.experienceAward,
                ),
            ),
        )
        return CompileFileResponse(
            stateId = stateId,
            compiledFile = compiledFile,
            pettyCashAfter = updated.economy.pettyCash,
            experienceAfter = updated.stats.experienceByFamily[binaryMetadata.scriptFamily] ?: 0,
            version = updated.version,
        )
    }
}

class DecompileFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
) : RequestCommand<DecompileFileResponse> {
    override val name: String = "decompilefile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): DecompileFileResponse {
        val state = context.requireExistingState(stateId)
        val compiledFile = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No compiled file found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        require(
            compiledFile.kind == StoredFileKind.APPLICATION_BINARY ||
                compiledFile.kind == StoredFileKind.FIREWALL_BINARY ||
                compiledFile.kind == StoredFileKind.EQUIPMENT_BINARY,
        ) {
            "Only compiled binaries can be decompiled."
        }
        compiledFile.validateScriptBundle()
        val binaryMetadata = requireNotNull(compiledFile.compiledBinary) {
            "Compiled binary metadata is required for ${compiledFile.name}."
        }
        val remainingSourceFile = compiledFile.decrementQuantity()
        val decompiledName = compiledFile.name.removeSuffix(".bin")
        val decompiledPath = buildFilePath(parentDirectoryOf(compiledFile.path), decompiledName)
        val existingSource = state.filesystem.filesByPath[decompiledPath]
        val decompiledFile = StoredFile(
            path = decompiledPath,
            name = decompiledName,
            kind = StoredFileKind.SCRIPT_SOURCE,
            contents = compiledFile.contents,
            description = compiledFile.description,
            quantity = (existingSource?.quantity ?: 0) + 1,
            maker = compiledFile.maker,
            compileCost = compiledFile.compileCost,
            cpuCost = compiledFile.cpuCost,
            compiledBinary = binaryMetadata,
            scriptBundle = compiledFile.scriptBundle,
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                FileDecompiledEvent(
                    sourceFilePath = compiledFile.path,
                    remainingSourceFile = remainingSourceFile,
                    decompiledFile = decompiledFile,
                    pettyCashDelta = compiledFile.compileCost,
                    scriptFamily = binaryMetadata.scriptFamily,
                    experienceDelta = -binaryMetadata.experienceAward,
                ),
            ),
        )
        return DecompileFileResponse(
            stateId = stateId,
            decompiledFile = decompiledFile,
            pettyCashAfter = updated.economy.pettyCash,
            experienceAfter = updated.stats.experienceByFamily[binaryMetadata.scriptFamily] ?: 0,
            version = updated.version,
        )
    }
}

class InstallApplicationCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
    private val portNumber: Int,
) : RequestCommand<InstallApplicationResponse> {
    override val name: String = "installapplication"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): InstallApplicationResponse {
        val state = context.requireExistingState(stateId)
        val source = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No application binary found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        require(source.kind == StoredFileKind.APPLICATION_BINARY) {
            "Only compiled application binaries can be installed."
        }
        source.validateScriptBundle()
        val metadata = requireNotNull(source.compiledBinary) {
            "Compiled application metadata is required for ${source.name}."
        }
        val remainingSource = source.decrementQuantity()
        val installedApplication = InstalledApplication(
            name = source.name,
            kind = metadata.applicationKind ?: ApplicationKind.GENERIC,
            maker = source.maker,
            binaryPath = source.path,
            cpuCost = source.cpuCost,
            banking = metadata.bankingApplication || metadata.applicationKind == ApplicationKind.BANKING,
            scriptBundle = source.scriptBundle,
        )
        val existingPort = state.ports.firstOrNull { it.number == portNumber }
        val defaultBankPort = if (installedApplication.banking && state.economy.defaultBankPort == null) {
            portNumber
        } else {
            null
        }
        val defaultPort = when {
            installedApplication.kind == ApplicationKind.HTTP || installedApplication.kind == ApplicationKind.FTP -> {
                existingPort?.defaultPort == true ||
                    state.ports.none { it.defaultPort && it.installedApplication?.kind == installedApplication.kind }
            }
            installedApplication.banking -> defaultBankPort == portNumber || state.economy.defaultBankPort == portNumber
            else -> existingPort?.defaultPort ?: false
        }
        val updatedPort = (existingPort ?: PortState(number = portNumber)).copy(
            type = installedApplication.kind.name.lowercase(),
            enabled = true,
            installedApplication = installedApplication,
            defaultPort = defaultPort,
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                ApplicationInstalledEvent(
                    sourceFilePath = source.path,
                    remainingSourceFile = remainingSource,
                    portState = updatedPort,
                    defaultBankPort = defaultBankPort,
                ),
            ),
        )
        return InstallApplicationResponse(
            stateId = stateId,
            portNumber = portNumber,
            installedApplication = installedApplication,
            defaultBankPort = updated.economy.defaultBankPort,
            version = updated.version,
        )
    }
}

class InstallEquipmentCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
    private val slot: EquipmentSlot,
) : RequestCommand<InstallEquipmentResponse> {
    override val name: String = "installequipment"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): InstallEquipmentResponse {
        val state = context.requireExistingState(stateId)
        val source = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No equipment binary found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        require(source.kind == StoredFileKind.EQUIPMENT_BINARY) {
            "Only compiled equipment binaries can be installed into slots."
        }
        val metadata = requireNotNull(source.compiledBinary) {
            "Equipment metadata is required for ${source.name}."
        }
        require(metadata.equipmentSlot == slot) {
            "Equipment file ${source.name} cannot be installed into slot $slot."
        }
        val remainingSource = source.decrementQuantity()
        val equipment = InstalledEquipment(
            slot = slot,
            name = source.name,
            maker = source.maker,
            binaryPath = source.path,
            cpuBoost = source.cpuCost,
            memoryBoost = metadata.equipmentSlot.takeIf { it == EquipmentSlot.MEMORY }?.ordinal ?: 0,
            storageBoost = metadata.equipmentSlot.takeIf { it == EquipmentSlot.STORAGE }?.ordinal ?: 0,
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                EquipmentInstalledEvent(
                    sourceFilePath = source.path,
                    remainingSourceFile = remainingSource,
                    slot = slot,
                    equipment = equipment,
                ),
            ),
        )
        return InstallEquipmentResponse(
            stateId = stateId,
            slot = slot,
            equipment = equipment,
            version = updated.version,
        )
    }
}

class InstallFirewallCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
    private val portNumber: Int,
) : RequestCommand<InstallFirewallResponse> {
    override val name: String = "installfirewall"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): InstallFirewallResponse {
        val state = context.requireExistingState(stateId)
        val source = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No firewall binary found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        require(source.kind == StoredFileKind.FIREWALL_BINARY) {
            "Only compiled firewall binaries can be installed."
        }
        val metadata = requireNotNull(source.compiledBinary) {
            "Firewall metadata is required for ${source.name}."
        }
        val remainingSource = source.decrementQuantity()
        val existingPort = state.ports.firstOrNull { it.number == portNumber }
        val existingFirewall = existingPort?.installedFirewall
        val returnedFirewall = existingFirewall?.toStoredFile()
        val installedFirewall = InstalledFirewall(
            name = source.name,
            kind = metadata.firewallKind ?: FirewallKind.CUSTOM,
            maker = source.maker,
            binaryPath = source.path,
            strength = metadata.strength,
            cpuCost = source.cpuCost,
        )
        val updatedPort = (existingPort ?: PortState(number = portNumber)).copy(
            installedFirewall = installedFirewall,
        )
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                FirewallInstalledEvent(
                    sourceFilePath = source.path,
                    remainingSourceFile = remainingSource,
                    portState = updatedPort,
                    returnedFirewall = returnedFirewall,
                ),
            ),
        )
        return InstallFirewallResponse(
            stateId = stateId,
            portNumber = portNumber,
            installedFirewall = installedFirewall,
            returnedFirewall = returnedFirewall,
            version = updated.version,
        )
    }
}

@Serializable
data class DepositPayload(
    val amount: Double,
    val ip: String,
    val port: Int,
)

@Serializable
data class WithdrawPayload(
    val amount: Double,
    val ip: String,
    val port: Int,
)

@Serializable
data class TransferPayload(
    val amount: Double,
    val ip: String,
    val targetIp: String,
    val port: Int,
)

@Serializable
data class SellFileCommandPayload(
    val ip: String,
    val location: String? = null,
    val fileName: String,
    val compileCost: Double? = null,
    val quantity: Int? = null,
)

@Serializable
data class SellFileMultiEntry(
    val path: String,
    val name: String,
    val maker: String = "",
    val quantity: Int,
)

@Serializable
data class SellFileMultiCommandPayload(
    val ip: String,
    val allFiles: List<SellFileMultiEntry> = emptyList(),
)

@Serializable
data class RequestPurchasePayload(
    val targetIp: String,
    val sourceIp: String,
    val fileName: String,
    val quantity: Int,
)

@Serializable
data class FacebookDepositPayload(
    val amount: Double,
    val ip: String,
    @SerialName("defaultPort")
    val defaultPort: Int,
)

@Serializable
data class FacebookWithdrawPayload(
    val amount: Double,
    val ip: String,
    @SerialName("defaultPort")
    val defaultPort: Int,
)

@Serializable
data class FacebookTransferPayload(
    val amount: Double,
    val ip: String,
    @SerialName("ip2")
    val targetIp: String,
    @SerialName("defaultPort")
    val defaultPort: Int,
)

class DepositCommand(
    private val stateId: GameStateId,
    private val amount: Double,
    private val portNumber: Int,
) : RequestCommand<BankTransactionResponse> {
    override val name: String = "deposit"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): BankTransactionResponse {
        require(amount > 0.0) { "Deposit amount must be positive." }
        val state = context.requireExistingState(stateId)
        require(state.hasBankPort(portNumber)) {
            "Deposit requires an active banking application on port $portNumber."
        }
        val appliedAmount = minOf(amount, state.economy.pettyCash)
        require(appliedAmount > 0.0) { "Not enough petty cash to deposit." }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(
                    pettyCashDelta = -appliedAmount,
                    bankMoneyDelta = appliedAmount,
                ),
            ),
        )

        return BankTransactionResponse(
            stateId = stateId,
            operation = "deposit",
            portNumber = portNumber,
            requestedAmount = amount,
            appliedAmount = appliedAmount,
            pettyCashAfter = updated.economy.pettyCash,
            bankMoneyAfter = updated.economy.bankMoney,
            version = updated.version,
        )
    }
}

class WithdrawCommand(
    private val stateId: GameStateId,
    private val amount: Double,
    private val portNumber: Int,
) : RequestCommand<BankTransactionResponse> {
    override val name: String = "withdraw"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): BankTransactionResponse {
        require(amount > 0.0) { "Withdraw amount must be positive." }
        val state = context.requireExistingState(stateId)
        require(state.hasBankPort(portNumber)) {
            "Withdraw requires an active banking application on port $portNumber."
        }
        val appliedAmount = minOf(amount, state.economy.bankMoney)
        require(appliedAmount > 0.0) { "Not enough bank money to withdraw." }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(
                    pettyCashDelta = appliedAmount,
                    bankMoneyDelta = -appliedAmount,
                ),
            ),
        )

        return BankTransactionResponse(
            stateId = stateId,
            operation = "withdraw",
            portNumber = portNumber,
            requestedAmount = amount,
            appliedAmount = appliedAmount,
            pettyCashAfter = updated.economy.pettyCash,
            bankMoneyAfter = updated.economy.bankMoney,
            version = updated.version,
        )
    }
}

class TransferCommand(
    private val sourceStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val amount: Double,
    private val portNumber: Int,
) : RequestCommand<TransferResponse> {
    override val name: String = "transfer"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(sourceStateId, targetStateId)

    override suspend fun execute(context: CommandContext): TransferResponse {
        require(amount > 0.0) { "Transfer amount must be positive." }
        val states = context.loadStates(targetStateIds)
        val sourceState = requireNotNull(states[sourceStateId]) {
            "No game state exists for ${sourceStateId.value}."
        }
        val targetState = requireNotNull(states[targetStateId]) {
            "No game state exists for ${targetStateId.value}."
        }
        require(sourceState.hasBankPort(portNumber)) {
            "Transfer requires an active banking application on port $portNumber."
        }
        require(targetState.hasActiveDefaultBankPort()) {
            "Transfer target ${targetStateId.value} does not have an active default bank port."
        }

        val appliedAmount = minOf(amount, sourceState.economy.pettyCash)
        require(appliedAmount > 0.0) { "Not enough petty cash to transfer." }

        if (sourceStateId == targetStateId) {
            return TransferResponse(
                sourceStateId = sourceStateId,
                targetStateId = targetStateId,
                portNumber = portNumber,
                requestedAmount = amount,
                appliedAmount = 0.0,
                sourcePettyCashAfter = sourceState.economy.pettyCash,
                targetPettyCashAfter = targetState.economy.pettyCash,
                sourceVersion = sourceState.version,
                targetVersion = targetState.version,
            )
        }

        val updatedSource = context.appendEvents(
            id = sourceStateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = -appliedAmount),
            ),
        )
        val updatedTarget = context.appendEvents(
            id = targetStateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = appliedAmount),
            ),
        )

        return TransferResponse(
            sourceStateId = sourceStateId,
            targetStateId = targetStateId,
            portNumber = portNumber,
            requestedAmount = amount,
            appliedAmount = appliedAmount,
            sourcePettyCashAfter = updatedSource.economy.pettyCash,
            targetPettyCashAfter = updatedTarget.economy.pettyCash,
            sourceVersion = updatedSource.version,
            targetVersion = updatedTarget.version,
        )
    }
}

class SellFileCommand(
    private val stateId: GameStateId,
    private val path: String?,
    private val fileName: String,
    private val compileCost: Double? = null,
) : RequestCommand<SellFileResponse> {
    override val name: String = "sellfile"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SellFileResponse {
        val state = context.requireExistingState(stateId)
        val file = requireNotNull(state.filesystem.resolveFile(path, fileName)) {
            "No file found at ${normalizeDirectoryPath(path, state.filesystem.currentPath)}/$fileName"
        }
        val resolvedCompileCost = compileCost?.takeIf { it > 0.0 } ?: file.compileCost.coerceAtLeast(0.0)
        val makerFloor = makerFloorFor(file.maker)
        val occupancyFactor = 1 + max(file.quantity, 1)
        val price = max(
            resolvedCompileCost * 2.0 - (resolvedCompileCost * 0.01 * occupancyFactor),
            makerFloor,
        )

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                StoreFilePricedEvent(
                    filePath = file.path,
                    price = price,
                ),
            ),
        )

        return SellFileResponse(
            stateId = stateId,
            file = requireNotNull(updated.filesystem.filesByPath[file.path]),
            version = updated.version,
        )
    }
}

class SellFileMultiCommand(
    private val stateId: GameStateId,
    private val storeStateId: GameStateId,
    private val entries: List<SellFileMultiEntry>,
) : RequestCommand<SellFileMultiResponse> {
    override val name: String = "sellfilemulti"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId, storeStateId)

    override suspend fun execute(context: CommandContext): SellFileMultiResponse {
        val state = context.requireExistingState(stateId)
        context.requireExistingState(storeStateId)

        val soldItems = entries.mapNotNull { entry ->
            val source = state.filesystem.resolveFile(entry.path, entry.name) ?: return@mapNotNull null
            val requestedQuantity = entry.quantity.coerceAtLeast(0)
            if (requestedQuantity <= 0) {
                return@mapNotNull null
            }
            val soldQuantity = minOf(requestedQuantity, source.quantity)
            if (soldQuantity <= 0) {
                return@mapNotNull null
            }
            val resolvedMaker = source.maker.ifBlank { entry.maker }
            val unitPrice = source.price.takeIf { it > 0.0 } ?: makerFloorFor(resolvedMaker)
            val remaining = source.copy(quantity = source.quantity - soldQuantity).takeIf { it.quantity > 0 }
            SoldStoreFile(
                sourceFilePath = source.path,
                remainingSourceFile = remaining,
                creditedPettyCash = unitPrice * soldQuantity,
                storeCopy = source.copy(
                    path = buildFilePath("/Store", source.name),
                    quantity = soldQuantity,
                    maker = resolvedMaker,
                    price = unitPrice,
                ),
            )
        }
        require(soldItems.isNotEmpty()) { "No sellable files were provided." }

        val updatedState = context.appendEvents(
            id = stateId,
            events = listOf(
                StoreFilesLiquidatedEvent(
                    soldItems = soldItems.map { sold ->
                        StoreLiquidationLineItem(
                            sourceFilePath = sold.sourceFilePath,
                            remainingSourceFile = sold.remainingSourceFile,
                            creditedPettyCash = sold.creditedPettyCash,
                        )
                    },
                ),
            ),
        )
        context.appendEvents(
            id = storeStateId,
            events = listOf(
                StoreInventoryReceivedEvent(soldItems.map { it.storeCopy }),
            ),
        )

        return SellFileMultiResponse(
            stateId = stateId,
            storeStateId = storeStateId,
            soldFiles = soldItems.map { it.storeCopy },
            creditedAmount = soldItems.sumOf { it.creditedPettyCash },
            version = updatedState.version,
        )
    }
}

class RequestPurchaseCommand(
    private val buyerStateId: GameStateId,
    private val sellerStateId: GameStateId,
    private val fileName: String,
    private val requestedQuantity: Int,
    private val revenueTargetHint: GameStateId? = null,
) : RequestCommand<PurchaseResponse> {
    override val name: String = "requestpurchase"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = buildSet {
        add(buyerStateId)
        add(sellerStateId)
        if (revenueTargetHint != null) {
            add(revenueTargetHint)
        }
    }

    override suspend fun execute(context: CommandContext): PurchaseResponse {
        require(buyerStateId != sellerStateId) { "Self-purchase is not allowed." }
        val quantity = requestedQuantity.coerceAtLeast(1)
        val states = context.loadStates(targetStateIds)
        val buyerState = requireNotNull(states[buyerStateId]) {
            "No buyer state exists for ${buyerStateId.value}."
        }
        val sellerState = requireNotNull(states[sellerStateId]) {
            "No seller state exists for ${sellerStateId.value}."
        }
        val listing = requireNotNull(sellerState.filesystem.resolveFile("/Store", fileName)) {
            "No store listing named $fileName exists for ${sellerStateId.value}."
        }
        require(listing.price > 0.0) { "Store listing $fileName is not purchasable." }

        val fulfilledQuantity = minOf(quantity, listing.quantity)
        require(fulfilledQuantity > 0) { "Requested purchase quantity is not available." }

        val totalPrice = listing.price * fulfilledQuantity
        require(buyerState.economy.pettyCash >= totalPrice) { "Not enough petty cash to purchase $fileName." }

        val purchasedPath = buildFilePath("/", listing.name)
        require(buyerState.canStoreFileAt(purchasedPath)) {
            "Buyer ${buyerStateId.value} does not have enough filesystem capacity for ${listing.name}."
        }

        val revenueTargetStateId = resolveRevenueTargetStateId(
            context = context,
            sellerState = sellerState,
            fallback = revenueTargetHint ?: sellerStateId,
        )
        val remainingListing = listing.copy(quantity = listing.quantity - fulfilledQuantity).takeIf { it.quantity > 0 }
        val purchasedFile = listing.copy(
            path = purchasedPath,
            quantity = fulfilledQuantity,
        )

        val updatedSeller = context.appendEvents(
            id = sellerStateId,
            events = listOf(
                StoreListingPurchasedEvent(
                    listingPath = listing.path,
                    remainingListing = remainingListing,
                    pettyCashDelta = if (revenueTargetStateId == sellerStateId) totalPrice else 0.0,
                ),
            ),
        )
        val updatedBuyer = context.appendEvents(
            id = buyerStateId,
            events = listOf(
                PurchasedFileReceivedEvent(
                    file = purchasedFile,
                    pettyCashDelta = -totalPrice,
                ),
            ),
        )
        val updatedRevenueTarget = if (revenueTargetStateId == sellerStateId) {
            updatedSeller
        } else {
            context.appendEvents(
                id = revenueTargetStateId,
                events = listOf(
                    EconomyBalanceAdjustedEvent(pettyCashDelta = totalPrice),
                ),
            )
        }

        return PurchaseResponse(
            buyerStateId = buyerStateId,
            sellerStateId = sellerStateId,
            revenueTargetStateId = revenueTargetStateId,
            purchasedFile = purchasedFile,
            fulfilledQuantity = fulfilledQuantity,
            totalPrice = totalPrice,
            buyerVersion = updatedBuyer.version,
            sellerVersion = updatedSeller.version,
            revenueTargetVersion = updatedRevenueTarget.version,
        )
    }
}

@Serializable
data class RequestDirectoryPayload(
    val path: String? = null,
)

@Serializable
data class RequestSecondaryDirectoryPayload(
    val path: String? = null,
    val targetIp: String,
    val port: Int,
)

@Serializable
data class RequestFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class CreateFolderPayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class DeleteFolderPayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class DeleteFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class DeleteMultiPayload(
    val path: String? = null,
    val fileNames: List<String> = emptyList(),
    val directoryNames: List<String> = emptyList(),
)

@Serializable
data class SaveFilePayload(
    val path: String? = null,
    val file: StoredFile,
)

@Serializable
data class CompileFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class DecompileFilePayload(
    val path: String? = null,
    val name: String,
)

@Serializable
data class InstallApplicationPayload(
    val path: String? = null,
    val name: String,
    val portNumber: Int,
)

@Serializable
data class InstallEquipmentPayload(
    val path: String? = null,
    val name: String,
    val slot: EquipmentSlot,
)

@Serializable
data class InstallFirewallPayload(
    val path: String? = null,
    val name: String,
    val portNumber: Int,
)

internal suspend fun CommandContext.requireExistingState(stateId: GameStateId): ComputerState {
    return requireNotNull(loadState(stateId)) {
        "No game state exists for ${stateId.value}."
    }
}

private fun StoredFile.validateScriptBundle(): StoredFile {
    val scriptFamily = compiledBinary?.scriptFamily
    if (scriptFamily == ScriptFamily.HTTP) {
        requireNotNull(scriptBundle) {
            "HTTP script files must include a script bundle."
        }
        require(scriptBundle.family == ScriptFamily.HTTP) {
            "HTTP script bundles must use the HTTP family."
        }
        require(
            ProgramScriptSlot.ENTER in scriptBundle.scriptsBySlot &&
                ProgramScriptSlot.EXIT in scriptBundle.scriptsBySlot &&
                ProgramScriptSlot.SUBMIT in scriptBundle.scriptsBySlot,
        ) {
            "HTTP script bundles must contain enter, exit, and submit scripts."
        }
    }
    return this
}

private fun defaultCompiledName(
    sourceName: String,
    metadata: CompiledBinaryMetadata,
): String {
    return metadata.outputName.ifBlank {
        val base = sourceName.substringBeforeLast('.')
        "$base.bin"
    }
}

private fun StoredFile.toCompiledBinary(
    path: String,
    name: String,
    quantity: Int,
): StoredFile {
    val metadata = requireNotNull(compiledBinary) {
        "Compiled binary metadata is required for $name."
    }
    val targetKind = when {
        metadata.applicationKind != null || metadata.bankingApplication -> StoredFileKind.APPLICATION_BINARY
        metadata.firewallKind != null -> StoredFileKind.FIREWALL_BINARY
        metadata.equipmentSlot != null -> StoredFileKind.EQUIPMENT_BINARY
        else -> StoredFileKind.APPLICATION_BINARY
    }
    return copy(
        path = path,
        name = name,
        kind = targetKind,
        quantity = quantity,
    )
}

private fun StoredFile.decrementQuantity(): StoredFile? {
    return if (quantity <= 1) {
        null
    } else {
        copy(quantity = quantity - 1)
    }
}

private fun InstalledFirewall.toStoredFile(): StoredFile {
    val fileName = if (name.endsWith(".bin")) name else "$name.bin"
    return StoredFile(
        path = buildFilePath("/firewalls", fileName),
        name = fileName,
        kind = StoredFileKind.FIREWALL_BINARY,
        description = "Returned replaced firewall",
        quantity = 1,
        maker = maker,
        compileCost = 0.0,
        cpuCost = cpuCost,
        price = 0.0,
        compiledBinary = CompiledBinaryMetadata(
            firewallKind = kind,
            strength = strength,
        ),
    )
}

private data class SoldStoreFile(
    val sourceFilePath: String,
    val remainingSourceFile: StoredFile?,
    val creditedPettyCash: Double,
    val storeCopy: StoredFile,
)

private suspend fun resolveRevenueTargetStateId(
    context: CommandContext,
    sellerState: ComputerState,
    fallback: GameStateId,
): GameStateId {
    val preferred = sellerState.website.storeRevenueTargetStateId ?: fallback
    return if (context.loadState(preferred) != null) preferred else sellerState.id
}

internal fun ComputerState.hasBankPort(portNumber: Int): Boolean {
    return ports.any { port ->
        port.number == portNumber &&
            port.enabled &&
            (port.installedApplication?.banking == true || port.installedApplication?.kind == ApplicationKind.BANKING)
    }
}

internal fun ComputerState.hasActiveDefaultBankPort(): Boolean {
    val defaultPort = economy.defaultBankPort ?: return false
    return hasBankPort(defaultPort)
}

private fun ComputerState.canStoreFileAt(filePath: String): Boolean {
    if (filesystem.filesByPath.containsKey(filePath)) {
        return true
    }
    if (hardware.hdMaximum <= 0) {
        return true
    }
    return filesystem.filesByPath.size < hardware.hdMaximum
}

private fun makerFloorFor(maker: String): Double {
    return MAKER_FLOOR_BY_NAME[maker] ?: 0.0
}

private val MAKER_FLOOR_BY_NAME: Map<String, Double> = linkedMapOf(
    "Alexander" to 15.0,
    "Low" to 30.0,
    "Medium" to 150.0,
    "High" to 1500.0,
    "Rare" to 15000.0,
    "Holiday" to 250.0,
    "I" to 15.0,
    "II" to 23.0,
    "III" to 36.0,
    "IV" to 56.0,
    "V" to 87.0,
    "VI" to 134.0,
    "VII" to 208.0,
    "VIII" to 322.0,
    "IX" to 500.0,
    "X" to 775.0,
    "XI" to 1201.0,
    "XII" to 1861.0,
    "XIII" to 2885.0,
    "XIV" to 4471.0,
    "XV" to 6930.0,
    "XVI" to 10742.0,
    "XVII" to 16649.0,
    "XVIII" to 25807.0,
    "XIX" to 40000.0,
    "XX" to 62000.0,
    "Trash.I" to 15.0,
    "Trash.II" to 17.0,
    "Trash.III" to 19.0,
    "Trash.IV" to 21.0,
    "Trash.V" to 24.0,
    "Trash.VI" to 27.0,
    "Trash.VII" to 30.0,
)
