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

class PutFileCommand(
    private val requesterStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val portNumber: Int,
    private val fileName: String,
    private val fetchPath: String?,
    private val targetPath: String?,
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

    val updatedSource: ComputerState
    val updatedRequester: ComputerState
    if (requesterStateId == targetStateId) {
        val updated = context.appendEvents(
            id = requesterStateId,
            events = sourceEvents + destinationEvents,
        )
        updatedSource = updated
        updatedRequester = updated
    } else {
        updatedSource = context.appendEvents(
            id = if (operation == "get") targetStateId else requesterStateId,
            events = sourceEvents,
        )
        updatedRequester = context.appendEvents(
            id = if (operation == "get") requesterStateId else targetStateId,
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
        requesterVersion = if (operation == "get") updatedRequester.version else updatedSource.version,
        targetVersion = if (operation == "get") updatedSource.version else updatedRequester.version,
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
