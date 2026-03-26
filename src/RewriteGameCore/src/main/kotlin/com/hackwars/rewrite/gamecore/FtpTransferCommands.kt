package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetFilePayload(
    val ip: String? = null,
    val port: Int,
    val name: String? = null,
    val fetchPath: String? = null,
    val putPath: String? = null,
    @SerialName("targetIP")
    val targetIp: String,
    val password: String? = null,
    val quantity: Int? = null,
)

@Serializable
data class PutFilePayload(
    val ip: String? = null,
    val port: Int,
    val name: String? = null,
    val fetchPath: String? = null,
    val putPath: String? = null,
    @SerialName("targetIP")
    val targetIp: String,
    val password: String? = null,
    val quantity: Int? = null,
)

@Serializable
data class MalGetPayload(
    val ip: String? = null,
    val port: Int,
    val name: String? = null,
    val fetchPath: String? = null,
    val putPath: String? = null,
    @SerialName("targetIP")
    val targetIp: String,
    val attackPort: Int? = null,
)

@Serializable
data class FtpTransferResponse(
    val requesterStateId: GameStateId,
    val targetStateId: GameStateId,
    val targetPort: Int,
    val operation: String,
    val file: StoredFile,
    val fulfilledQuantity: Int,
    val message: String,
    val requesterVersion: Long,
    val targetVersion: Long,
)

class GetFileCommand(
    private val requesterStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val portNumber: Int,
    private val fileName: String,
    private val fetchPath: String?,
    private val targetPath: String?,
    private val password: String? = null,
    private val ftpPasswordRepository: FtpPasswordRepository = NoOpFtpPasswordRepository,
    private val requestedQuantity: Int = 1,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<FtpTransferResponse> {
    override val name: String = "get"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId, targetStateId)

    override suspend fun execute(context: CommandContext): FtpTransferResponse {
        val states = context.loadStates(targetStateIds)
        val requesterState = requireNotNull(states[requesterStateId]) {
            "No game state exists for ${requesterStateId.value}."
        }
        val targetState = requireNotNull(states[targetStateId]) {
            "No game state exists for ${targetStateId.value}."
        }
        targetState.requireRemoteFtpPortAccess(
            portNumber = portNumber,
            now = clock(),
            commandName = name,
        )
        requireMatchingFtpPassword(
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            providedPassword = password,
            ftpPasswordRepository = ftpPasswordRepository,
        )

        val remoteDirectory = normalizeDirectoryPath(targetPath, targetState.filesystem.currentPath)
        val localDirectory = normalizeDirectoryPath(fetchPath, requesterState.filesystem.currentPath)
        val sourceFile = requireNotNull(targetState.filesystem.resolveFile(remoteDirectory, fileName)) {
            "Remote file $fileName is missing from $remoteDirectory on ${targetStateId.value}."
        }
        val quantity = requestedQuantity.coerceAtLeast(1)
        require(sourceFile.quantity >= quantity) {
            "Remote file $fileName only has ${sourceFile.quantity} available on ${targetStateId.value}."
        }

        val remainingRemoteFile = sourceFile.decrementBy(quantity)
        val deliveredFile = sourceFile.toDeliveredCopy(localDirectory, quantity)
        val existingRequesterFile = requesterState.filesystem.resolveFile(localDirectory, fileName)
        val updatedRequesterFile = existingRequesterFile
            ?.copy(quantity = existingRequesterFile.quantity + quantity)
            ?: deliveredFile

        return applyTransfer(
            context = context,
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            sourceFilePath = sourceFile.path,
            remainingSourceFile = remainingRemoteFile,
            destinationFile = updatedRequesterFile,
            fulfilledQuantity = quantity,
            operation = name,
            message = "ftp-get-complete",
            portNumber = portNumber,
        )
    }
}

class MalGetCommand(
    private val requesterStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val portNumber: Int,
    private val fileName: String?,
    private val fetchPath: String?,
    private val targetPath: String?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<FtpTransferResponse> {
    override val name: String = "malget"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId, targetStateId)

    override suspend fun execute(context: CommandContext): FtpTransferResponse {
        val states = context.loadStates(targetStateIds)
        val requesterState = requireNotNull(states[requesterStateId]) {
            "No game state exists for ${requesterStateId.value}."
        }
        val targetState = requireNotNull(states[targetStateId]) {
            "No game state exists for ${targetStateId.value}."
        }
        targetState.requireRemoteFtpPortAccess(
            portNumber = portNumber,
            now = clock(),
            commandName = name,
        )

        val remoteDirectory = normalizeDirectoryPath(fetchPath, targetState.filesystem.currentPath)
        val localDirectory = normalizeDirectoryPath(targetPath, requesterState.filesystem.currentPath)
        val sourceFile = fileName
            ?.let { targetState.filesystem.resolveFile(remoteDirectory, it) }
            ?: targetState.filesystem.listDirectory(remoteDirectory).files.firstOrNull()
        requireNotNull(sourceFile) {
            val requestedName = fileName ?: "<first-file>"
            "Remote file $requestedName is missing from $remoteDirectory on ${targetStateId.value}."
        }

        val remainingRemoteFile = sourceFile.decrementBy(1)
        val deliveredFile = sourceFile.toDeliveredCopy(localDirectory, 1)
        val existingRequesterFile = requesterState.filesystem.resolveFile(localDirectory, sourceFile.name)
        val updatedRequesterFile = existingRequesterFile
            ?.copy(quantity = existingRequesterFile.quantity + 1)
            ?: deliveredFile

        return applyTransfer(
            context = context,
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            sourceFilePath = sourceFile.path,
            remainingSourceFile = remainingRemoteFile,
            destinationFile = updatedRequesterFile,
            fulfilledQuantity = 1,
            operation = name,
            message = "ftp-malget-complete",
            portNumber = portNumber,
        )
    }
}

class PutFileCommand(
    private val requesterStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val portNumber: Int,
    private val fileName: String,
    private val fetchPath: String?,
    private val targetPath: String?,
    private val password: String? = null,
    private val ftpPasswordRepository: FtpPasswordRepository = NoOpFtpPasswordRepository,
    private val requestedQuantity: Int = 1,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<FtpTransferResponse> {
    override val name: String = "put"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId, targetStateId)

    override suspend fun execute(context: CommandContext): FtpTransferResponse {
        val states = context.loadStates(targetStateIds)
        val requesterState = requireNotNull(states[requesterStateId]) {
            "No game state exists for ${requesterStateId.value}."
        }
        val targetState = requireNotNull(states[targetStateId]) {
            "No game state exists for ${targetStateId.value}."
        }
        targetState.requireRemoteFtpPortAccess(
            portNumber = portNumber,
            now = clock(),
            commandName = name,
        )
        requireMatchingFtpPassword(
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            providedPassword = password,
            ftpPasswordRepository = ftpPasswordRepository,
        )

        val localDirectory = normalizeDirectoryPath(fetchPath, requesterState.filesystem.currentPath)
        val remoteDirectory = normalizeDirectoryPath(targetPath, targetState.filesystem.currentPath)
        val sourceFile = requireNotNull(requesterState.filesystem.resolveFile(localDirectory, fileName)) {
            "Local file $fileName is missing from $localDirectory on ${requesterStateId.value}."
        }
        val quantity = requestedQuantity.coerceAtLeast(1)
        require(sourceFile.quantity >= quantity) {
            "Local file $fileName only has ${sourceFile.quantity} available on ${requesterStateId.value}."
        }

        val remainingRequesterFile = sourceFile.decrementBy(quantity)
        val deliveredFile = sourceFile.toDeliveredCopy(remoteDirectory, quantity)
        val existingTargetFile = targetState.filesystem.resolveFile(remoteDirectory, fileName)
        val updatedTargetFile = existingTargetFile
            ?.copy(quantity = existingTargetFile.quantity + quantity)
            ?: deliveredFile

        return applyTransfer(
            context = context,
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            sourceFilePath = sourceFile.path,
            remainingSourceFile = remainingRequesterFile,
            destinationFile = updatedTargetFile,
            fulfilledQuantity = quantity,
            operation = name,
            message = "ftp-put-complete",
            portNumber = portNumber,
        )
    }
}

private suspend fun applyTransfer(
    context: CommandContext,
    requesterStateId: GameStateId,
    targetStateId: GameStateId,
    sourceFilePath: String,
    remainingSourceFile: StoredFile?,
    destinationFile: StoredFile,
    fulfilledQuantity: Int,
    operation: String,
    message: String,
    portNumber: Int,
): FtpTransferResponse {
    val sourceEvents = buildList {
        add(FileDeletedEvent(sourceFilePath))
        if (remainingSourceFile != null) {
            add(FileSavedEvent(remainingSourceFile))
        }
    }
    val destinationEvents = listOf(FileSavedEvent(destinationFile))
    val sourceOwnerStateId = when (operation) {
        "get", "malget" -> targetStateId
        else -> requesterStateId
    }
    val destinationOwnerStateId = when (operation) {
        "get", "malget" -> requesterStateId
        else -> targetStateId
    }
    val updatedStates = linkedMapOf<GameStateId, ComputerState>()

    if (requesterStateId == targetStateId) {
        updatedStates[requesterStateId] = context.appendEvents(
            id = requesterStateId,
            events = sourceEvents + destinationEvents,
        )
    } else {
        updatedStates[sourceOwnerStateId] = context.appendEvents(
            id = sourceOwnerStateId,
            events = sourceEvents,
        )
        updatedStates[destinationOwnerStateId] = context.appendEvents(
            id = destinationOwnerStateId,
            events = destinationEvents,
        )
    }

    return FtpTransferResponse(
        requesterStateId = requesterStateId,
        targetStateId = targetStateId,
        targetPort = portNumber,
        operation = operation,
        file = destinationFile,
        fulfilledQuantity = fulfilledQuantity,
        message = message,
        requesterVersion = requireNotNull(updatedStates[requesterStateId]).version,
        targetVersion = requireNotNull(updatedStates[targetStateId]).version,
    )
}

private fun StoredFile.decrementBy(quantity: Int): StoredFile? {
    return if (this.quantity <= quantity) {
        null
    } else {
        copy(quantity = this.quantity - quantity)
    }
}

private fun StoredFile.toDeliveredCopy(
    destinationDirectory: String,
    quantity: Int,
): StoredFile {
    return copy(
        path = buildFilePath(destinationDirectory, name),
        quantity = quantity,
    )
}

internal fun ComputerState.requireRemoteFtpPortAccess(
    portNumber: Int,
    now: Long,
    commandName: String,
): PortState {
    val portState = requireRemotePortAccess(
        portNumber = portNumber,
        now = now,
        allowFrozen = false,
        allowOverheated = false,
        commandName = commandName,
    )
    require(portState.type.equals("ftp", ignoreCase = true) || portState.installedApplication?.kind == ApplicationKind.FTP) {
        "Target port $portNumber on ${id.value} is not an FTP port for $commandName."
    }
    return portState
}

private suspend fun requireMatchingFtpPassword(
    requesterStateId: GameStateId,
    targetStateId: GameStateId,
    providedPassword: String?,
    ftpPasswordRepository: FtpPasswordRepository,
) {
    if (requesterStateId == targetStateId) {
        return
    }
    val expectedPassword = ftpPasswordRepository.load(targetStateId).orEmpty()
    if (expectedPassword.isEmpty()) {
        return
    }
    require(providedPassword.orEmpty() == expectedPassword) {
        "The password you provided to connect to this FTP site was incorrect."
    }
}
