package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.network.allowShopFtpSourceDirectory
import com.hackwars.rewrite.client.network.defaultPublicFtpTargetPort
import com.hackwars.rewrite.client.network.deriveFtpPortOptions
import com.hackwars.rewrite.client.network.reconcileShopFtpPortSelection
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientSellFilePayload
import com.hackwars.rewrite.protocol.ClientSellFileResponse
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
        assertEquals("LOCAL-IP", payload.ip)
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
                        stateId = "LOCAL-IP",
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
                playerIp = "LOCAL-IP",
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
