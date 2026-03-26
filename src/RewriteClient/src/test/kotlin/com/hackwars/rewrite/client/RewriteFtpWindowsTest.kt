package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.network.allowShopFtpSourceDirectory
import com.hackwars.rewrite.client.network.defaultPublicFtpTargetPort
import com.hackwars.rewrite.client.network.deriveFtpPortOptions
import com.hackwars.rewrite.client.network.reconcileShopFtpPortSelection
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFtpTransferResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientGetFilePayload
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientMalGetPayload
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientPutFilePayload
import com.hackwars.rewrite.protocol.ClientSellFilePayload
import com.hackwars.rewrite.protocol.ClientSellFileResponse
import com.hackwars.rewrite.protocol.ClientSetFtpPasswordPayload
import com.hackwars.rewrite.protocol.ClientSetFtpPasswordResponse
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteFtpWindowsTest {
    @Test
    fun sellFileHelperSendsExpectedPayload() = runTest {
        val sessionGateway = FakeFtpSessionGateway()
        val controller = testController(sessionGateway = sessionGateway, scheduler = testScheduler)
        acceptGameAuth(controller)

        val pending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.sellFile(
                path = "/Programs",
                fileName = "merchant.bin",
                compileCost = 25.0,
                quantity = 2,
            )
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val command = session.sentFrames.last().command!!
        val payload = RewriteClientJson.decode(
            ClientSellFilePayload.serializer(),
            command.payload.toByteArray(),
        )

        assertEquals("sellfile", command.command_name)
        assertEquals("192.0.2.10", payload.ip)
        assertEquals("/Programs", payload.location)
        assertEquals("merchant.bin", payload.fileName)
        assertEquals(25.0, payload.compileCost)
        assertEquals(2, payload.quantity)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = command.command_id,
                payload = RewriteClientJson.encode(
                    ClientSellFileResponse.serializer(),
                    ClientSellFileResponse(
                        stateId = "192.0.2.10",
                        file = ClientStoredFile(
                            path = "/Store/merchant.bin",
                            name = "merchant.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            price = 125.0,
                            quantity = 2,
                        ),
                        version = 4,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val result = pending.await()
        assertIs<RewriteGameCommandResult.Success<ClientSellFileResponse>>(result)
        assertEquals("merchant.bin", result.value.file.name)
    }

    @Test
    fun ftpTransferAndPasswordHelpersSendExpectedPayloads() = runTest {
        val sessionGateway = FakeFtpSessionGateway()
        val controller = testController(sessionGateway = sessionGateway, scheduler = testScheduler)
        acceptGameAuth(controller)

        val setPasswordPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestSetFtpPassword("vault")
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val setPasswordCommand = session.sentFrames.last().command!!
        val setPasswordPayload = RewriteClientJson.decode(
            ClientSetFtpPasswordPayload.serializer(),
            setPasswordCommand.payload.toByteArray(),
        )

        assertEquals("setftppassword", setPasswordCommand.command_name)
        assertEquals("192.0.2.10", setPasswordPayload.ip)
        assertEquals("vault", setPasswordPayload.password)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = setPasswordCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientSetFtpPasswordResponse.serializer(),
                    ClientSetFtpPasswordResponse(
                        stateId = "192.0.2.10",
                        passwordSet = true,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientSetFtpPasswordResponse>>(setPasswordPending.await())

        val putPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestPutFile(
                targetIp = "198.51.100.20",
                portNumber = 17,
                fileName = "upload.bin",
                localPath = "/Docs",
                remotePath = "/Public",
                password = "vault",
                quantity = 2,
            )
        }
        runCurrent()
        val putCommand = session.sentFrames.last().command!!
        val putPayload = RewriteClientJson.decode(
            ClientPutFilePayload.serializer(),
            putCommand.payload.toByteArray(),
        )
        assertEquals("put", putCommand.command_name)
        assertEquals("192.0.2.10", putPayload.ip)
        assertEquals("198.51.100.20", putPayload.targetIp)
        assertEquals("/Docs", putPayload.fetchPath)
        assertEquals("/Public", putPayload.putPath)
        assertEquals("vault", putPayload.password)
        assertEquals(2, putPayload.quantity)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = putCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientFtpTransferResponse.serializer(),
                    ClientFtpTransferResponse(
                        requesterStateId = "192.0.2.10",
                        targetStateId = "198.51.100.20",
                        targetPort = 17,
                        operation = "put",
                        file = ClientStoredFile(
                            path = "/Public/upload.bin",
                            name = "upload.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                            quantity = 2,
                        ),
                        fulfilledQuantity = 2,
                        message = "ftp-put-complete",
                        requesterVersion = 4,
                        targetVersion = 5,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientFtpTransferResponse>>(putPending.await())

        val getPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestGetFile(
                targetIp = "198.51.100.20",
                portNumber = 17,
                fileName = "remote.log",
                localPath = "/Inbox",
                remotePath = "/Public",
                password = "vault",
                quantity = 1,
            )
        }
        runCurrent()
        val getCommand = session.sentFrames.last().command!!
        val getPayload = RewriteClientJson.decode(
            ClientGetFilePayload.serializer(),
            getCommand.payload.toByteArray(),
        )
        assertEquals("get", getCommand.command_name)
        assertEquals("192.0.2.10", getPayload.ip)
        assertEquals("198.51.100.20", getPayload.targetIp)
        assertEquals("/Inbox", getPayload.fetchPath)
        assertEquals("/Public", getPayload.putPath)
        assertEquals("vault", getPayload.password)
        assertEquals(1, getPayload.quantity)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = getCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientFtpTransferResponse.serializer(),
                    ClientFtpTransferResponse(
                        requesterStateId = "192.0.2.10",
                        targetStateId = "198.51.100.20",
                        targetPort = 17,
                        operation = "get",
                        file = ClientStoredFile(
                            path = "/Inbox/remote.log",
                            name = "remote.log",
                            kind = ClientStoredFileKind.TEXT,
                            quantity = 1,
                        ),
                        fulfilledQuantity = 1,
                        message = "ftp-get-complete",
                        requesterVersion = 6,
                        targetVersion = 7,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientFtpTransferResponse>>(getPending.await())

        val malGetPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestMalGet(
                targetIp = "198.51.100.20",
                portNumber = 17,
                fileName = "loot.bin",
                remotePath = "/Secrets",
                attackPort = 44,
            )
        }
        runCurrent()
        val malGetCommand = session.sentFrames.last().command!!
        val malGetPayload = RewriteClientJson.decode(
            ClientMalGetPayload.serializer(),
            malGetCommand.payload.toByteArray(),
        )
        assertEquals("malget", malGetCommand.command_name)
        assertEquals("198.51.100.20", malGetPayload.ip)
        assertEquals("192.0.2.10", malGetPayload.targetIp)
        assertEquals("/Secrets", malGetPayload.fetchPath)
        assertNull(malGetPayload.putPath)
        assertEquals(44, malGetPayload.attackPort)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = malGetCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientFtpTransferResponse.serializer(),
                    ClientFtpTransferResponse(
                        requesterStateId = "192.0.2.10",
                        targetStateId = "198.51.100.20",
                        targetPort = 17,
                        operation = "malget",
                        file = ClientStoredFile(
                            path = "/loot.bin",
                            name = "loot.bin",
                            kind = ClientStoredFileKind.TEXT,
                            quantity = 1,
                        ),
                        fulfilledQuantity = 1,
                        message = "ftp-malget-complete",
                        requesterVersion = 8,
                        targetVersion = 9,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientFtpTransferResponse>>(malGetPending.await())
    }

    @Test
    fun ftpPortFilteringAndSelectionHonorLockedRules() {
        val snapshot = ClientGameSnapshot(
            ports = listOf(
                ClientPortState(
                    number = 8,
                    enabled = true,
                    dummy = false,
                    note = "default-ftp",
                    defaultPort = true,
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
                ),
                ClientPortState(
                    number = 4,
                    enabled = true,
                    dummy = false,
                    note = "ftp",
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
                ),
                ClientPortState(
                    number = 5,
                    enabled = false,
                    dummy = false,
                    note = "disabled",
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
                ),
                ClientPortState(
                    number = 6,
                    enabled = true,
                    dummy = true,
                    note = "dummy",
                    installedApplication = ClientInstalledApplication(name = "ftp", kind = "FTP"),
                ),
                ClientPortState(
                    number = 7,
                    enabled = true,
                    dummy = false,
                    note = "attack",
                    installedApplication = ClientInstalledApplication(name = "attack", kind = "ATTACK"),
                ),
            ),
        )

        val options = deriveFtpPortOptions(snapshot)

        assertEquals(listOf(4, 8), options.map { it.portNumber })
        assertEquals(8, reconcileShopFtpPortSelection(options, currentSelection = 8))
        assertEquals(8, reconcileShopFtpPortSelection(options, currentSelection = 99))
        assertEquals(8, defaultPublicFtpTargetPort(options))
        assertNull(reconcileShopFtpPortSelection(emptyList(), currentSelection = 4))
    }

    @Test
    fun shopFtpSourceDirectoryFilterExcludesStoreAndPublicRoots() {
        assertFalse(allowShopFtpSourceDirectory(ClientDirectoryEntry(path = "/Store", name = "Store")))
        assertFalse(allowShopFtpSourceDirectory(ClientDirectoryEntry(path = "/Public", name = "Public")))
        assertTrue(allowShopFtpSourceDirectory(ClientDirectoryEntry(path = "/Programs", name = "Programs")))
    }

    private fun testController(
        sessionGateway: RewriteServiceSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private fun acceptGameAuth(controller: RewriteRootController) {
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.authAccepted(
                connectionId = "conn-1",
                playFabId = "PF-LOCAL",
                playerIp = "192.0.2.10",
                heartbeatInterval = kotlin.time.Duration.parse("15s"),
                sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
            ),
        )
    }

    private class FakeFtpSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeFtpSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeFtpSession(service, onInboundFrame).also(sessions::add)
        }

        fun requireLatestGameSession(): FakeFtpSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeFtpSession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()

        override suspend fun send(frame: FrameEnvelope) {
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() {
            Unit
        }
    }
}
