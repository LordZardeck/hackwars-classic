package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FtpTransferCommandsTest {
    @Test
    fun getMovesRequestedQuantityFromRemoteToLocalAndPublishesFilesystemDeltas() = runTest {
        val localId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                localId to localState(
                    localId,
                    files = listOf(
                        storedFile("/Docs", "remote.log", quantity = 1, contents = "local copy"),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    files = listOf(
                        storedFile("/Secrets", "remote.log", quantity = 3, contents = "remote copy"),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", localId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = GetFileCommand(
                requesterStateId = localId,
                targetStateId = targetId,
                portNumber = 17,
                fileName = "remote.log",
                fetchPath = "/Docs",
                targetPath = "/Secrets",
                requestedQuantity = 2,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "ftp-get-1"),
            publisher = publisher,
        )

        val updatedLocal = requireNotNull(repository.load(localId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals("get", response.operation)
        assertEquals(localId, response.requesterStateId)
        assertEquals(targetId, response.targetStateId)
        assertEquals(2, response.fulfilledQuantity)
        assertEquals("/Docs/remote.log", response.file.path)
        assertEquals(3, requireNotNull(updatedLocal.filesystem.resolveFile("/Docs", "remote.log")).quantity)
        assertEquals(1, requireNotNull(updatedTarget.filesystem.resolveFile("/Secrets", "remote.log")).quantity)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("filesystem"), publisher.deltas.single { it.first == setOf("local-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
    }

    @Test
    fun putMovesRequestedQuantityFromLocalToRemoteAndPublishesFilesystemDeltas() = runTest {
        val localId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                localId to localState(
                    localId,
                    files = listOf(
                        storedFile("/Public", "upload.txt", quantity = 3, contents = "local upload"),
                    ),
                ),
                targetId to targetState(
                    targetId,
                    files = listOf(
                        storedFile("/Inbox", "upload.txt", quantity = 2, contents = "remote copy"),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", localId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = PutFileCommand(
                requesterStateId = localId,
                targetStateId = targetId,
                portNumber = 17,
                fileName = "upload.txt",
                fetchPath = "/Public",
                targetPath = "/Inbox",
                requestedQuantity = 2,
            ),
            metadata = CommandMetadata(connectionId = "local-conn", requestId = "ftp-put-1"),
            publisher = publisher,
        )

        val updatedLocal = requireNotNull(repository.load(localId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals("put", response.operation)
        assertEquals(localId, response.requesterStateId)
        assertEquals(targetId, response.targetStateId)
        assertEquals(2, response.fulfilledQuantity)
        assertEquals("/Inbox/upload.txt", response.file.path)
        assertEquals(1, requireNotNull(updatedLocal.filesystem.resolveFile("/Public", "upload.txt")).quantity)
        assertEquals(4, requireNotNull(updatedTarget.filesystem.resolveFile("/Inbox", "upload.txt")).quantity)
        assertEquals(2, publisher.deltas.size)
        assertEquals(setOf("filesystem"), publisher.deltas.single { it.first == setOf("local-conn") }.second.deltaKeys)
        assertEquals(setOf("filesystem"), publisher.deltas.single { it.first == setOf("target-conn") }.second.deltaKeys)
    }

    @Test
    fun transfersRejectNonFtpTargetPorts() = runTest {
        val localId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                localId to localState(localId, files = listOf(storedFile("/Public", "upload.txt"))),
                targetId to ComputerState.empty(targetId, playerIp = targetId.value).copy(
                    ports = listOf(PortState(number = 22, type = "ssh")),
                    filesystem = ComputerState.empty(targetId, playerIp = targetId.value).filesystem
                        .ensureDirectory("/Inbox"),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("local-conn", localId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val failure = assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = PutFileCommand(
                    requesterStateId = localId,
                    targetStateId = targetId,
                    portNumber = 22,
                    fileName = "upload.txt",
                    fetchPath = "/Public",
                    targetPath = "/Inbox",
                    requestedQuantity = 1,
                ),
                metadata = CommandMetadata(connectionId = "local-conn", requestId = "ftp-put-bad-port"),
                publisher = publisher,
            )
        }

        assertEquals("Target port 22 on TARGET-IP is not an FTP port for put.", failure.message)
    }

    private fun localState(
        stateId: GameStateId,
        files: List<StoredFile>,
    ): ComputerState {
        var filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .withCurrentPath("/Public")
            .ensureDirectory("/Public")
            .ensureDirectory("/Docs")
        files.forEach { file -> filesystem = filesystem.saveFile(file) }
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(filesystem = filesystem)
    }

    private fun targetState(
        stateId: GameStateId,
        files: List<StoredFile>,
    ): ComputerState {
        var filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .withCurrentPath("/Secrets")
            .ensureDirectory("/Secrets")
            .ensureDirectory("/Inbox")
        files.forEach { file -> filesystem = filesystem.saveFile(file) }
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(
            filesystem = filesystem,
            ports = listOf(
                PortState(number = 17, type = "ftp"),
                PortState(number = 22, type = "ssh"),
            ),
        )
    }

    private fun storedFile(
        directory: String,
        name: String,
        quantity: Int = 1,
        contents: String = "payload",
    ): StoredFile {
        return StoredFile(
            path = buildFilePath(directory, name),
            name = name,
            kind = StoredFileKind.TEXT,
            contents = contents,
            quantity = quantity,
        )
    }
}
