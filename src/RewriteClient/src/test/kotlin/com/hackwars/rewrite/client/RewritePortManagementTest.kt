package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.systems.RewritePortManagementRow
import com.hackwars.rewrite.client.systems.allowPortManagementApplicationFile
import com.hackwars.rewrite.client.systems.allowPortManagementFirewallFile
import com.hackwars.rewrite.client.systems.buildPortManagementRows
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientFirewallKind
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHealPortPayload
import com.hackwars.rewrite.protocol.ClientHealPortOutcome
import com.hackwars.rewrite.protocol.ClientHealPortResponse
import com.hackwars.rewrite.protocol.ClientInstallApplicationPayload
import com.hackwars.rewrite.protocol.ClientInstallApplicationResponse
import com.hackwars.rewrite.protocol.ClientInstallFirewallPayload
import com.hackwars.rewrite.protocol.ClientInstallFirewallResponse
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewritePortManagementTest {
    @Test
    fun buildPortManagementRowsReflectDecodedState() {
        val rows = buildPortManagementRows(
            ClientGameSnapshot(
                ports = listOf(
                    ClientPortState(
                        number = 6,
                        enabled = true,
                        defaultPort = true,
                        dummy = false,
                        health = 92.0,
                        healCount = 3,
                        note = "Bank main",
                        maxCpuCost = 10.0,
                        installedApplication = ClientInstalledApplication(
                            name = "bank.bin",
                            kind = "BANKING",
                            cpuCost = 2.5,
                        ),
                        installedFirewall = ClientInstalledFirewall(
                            name = "guard.fw",
                            kind = "BASIC",
                            cpuCost = 1.5,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                RewritePortManagementRow(
                    number = 6,
                    programLabel = "bank.bin",
                    firewallLabel = "guard.fw",
                    currentCpuCost = 4.0,
                    maxCpuCost = 10.0,
                    health = 92.0,
                    healsLeft = 7,
                    defaultPort = true,
                    dummy = false,
                    enabled = true,
                    note = "Bank main",
                    installedApplication = ClientInstalledApplication(
                        name = "bank.bin",
                        kind = "BANKING",
                        cpuCost = 2.5,
                    ),
                    installedFirewall = ClientInstalledFirewall(
                        name = "guard.fw",
                        kind = "BASIC",
                        cpuCost = 1.5,
                    ),
                ),
            ),
            rows,
        )
        assertEquals("4/10", rows.single().cpuDisplay)
        assertEquals("92", rows.single().healthDisplay)
    }

    @Test
    fun installChooserFiltersAllowOnlySupportedCompiledKinds() {
        val applicationFile = ClientStoredFile(
            path = "/Programs/http.bin",
            name = "http.bin",
            kind = ClientStoredFileKind.APPLICATION_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                applicationKind = ClientApplicationKind.HTTP,
            ),
        )
        val invalidApplicationFile = ClientStoredFile(
            path = "/Programs/unknown.bin",
            name = "unknown.bin",
            kind = ClientStoredFileKind.APPLICATION_BINARY,
        )
        val firewallFile = ClientStoredFile(
            path = "/Programs/basic.fw",
            name = "basic.fw",
            kind = ClientStoredFileKind.FIREWALL_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                firewallKind = ClientFirewallKind.BASIC,
            ),
        )
        val textFile = ClientStoredFile(
            path = "/Notes/readme.txt",
            name = "readme.txt",
            kind = ClientStoredFileKind.TEXT,
        )

        assertTrue(allowPortManagementApplicationFile(applicationFile))
        assertFalse(allowPortManagementApplicationFile(invalidApplicationFile))
        assertFalse(allowPortManagementApplicationFile(textFile))
        assertTrue(allowPortManagementFirewallFile(firewallFile))
        assertFalse(allowPortManagementFirewallFile(applicationFile))
    }

    @Test
    fun controllerHelpersSendExpectedHealAndInstallPayloads() = runTest {
        val sessionGateway = FakePortManagementSessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        acceptGameAuth(controller)

        try {
            val healRequest = async { controller.requestHealPort(6) }
            runCurrent()
            val healCommand = sessionGateway.requireLatestGameCommand()
            val healPayload = RewriteClientJson.decode(
                ClientHealPortPayload.serializer(),
                healCommand.payload.toByteArray(),
            )
            assertEquals("healport", healCommand.command_name)
            assertEquals("LOCAL-IP", healPayload.ip)
            assertEquals(6, healPayload.port)
            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = healCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientHealPortResponse.serializer(),
                        ClientHealPortResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            accepted = true,
                            outcome = ClientHealPortOutcome.SUCCESS,
                            message = "healport-succeeded",
                            chargedAmount = 5.0,
                            pettyCashAfter = 20.0,
                            healthAfter = 100.0,
                            healCountAfter = 1,
                            version = 2,
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientHealPortResponse>>(healRequest.await())

            val installApplicationRequest = async {
                controller.requestInstallApplication(
                    path = "/Programs",
                    name = "bank.bin",
                    portNumber = 6,
                )
            }
            runCurrent()
            val installApplicationCommand = sessionGateway.requireLatestGameCommand()
            val installApplicationPayload = RewriteClientJson.decode(
                ClientInstallApplicationPayload.serializer(),
                installApplicationCommand.payload.toByteArray(),
            )
            assertEquals("installapplication", installApplicationCommand.command_name)
            assertEquals("/Programs", installApplicationPayload.path)
            assertEquals("bank.bin", installApplicationPayload.name)
            assertEquals(6, installApplicationPayload.portNumber)
            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = installApplicationCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallApplicationResponse.serializer(),
                        ClientInstallApplicationResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            installedApplication = ClientInstalledApplication(
                                name = "bank.bin",
                                kind = "BANKING",
                                cpuCost = 2.5,
                            ),
                            defaultBankPort = 6,
                            version = 3,
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientInstallApplicationResponse>>(installApplicationRequest.await())

            val installFirewallRequest = async {
                controller.requestInstallFirewall(
                    path = "/Programs",
                    name = "guard.fw",
                    portNumber = 6,
                )
            }
            runCurrent()
            val installFirewallCommand = sessionGateway.requireLatestGameCommand()
            val installFirewallPayload = RewriteClientJson.decode(
                ClientInstallFirewallPayload.serializer(),
                installFirewallCommand.payload.toByteArray(),
            )
            assertEquals("installfirewall", installFirewallCommand.command_name)
            assertEquals("/Programs", installFirewallPayload.path)
            assertEquals("guard.fw", installFirewallPayload.name)
            assertEquals(6, installFirewallPayload.portNumber)
            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = installFirewallCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallFirewallResponse.serializer(),
                        ClientInstallFirewallResponse(
                            stateId = "LOCAL-IP",
                            portNumber = 6,
                            installedFirewall = ClientInstalledFirewall(
                                name = "guard.fw",
                                kind = "BASIC",
                                cpuCost = 1.5,
                            ),
                            version = 4,
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientInstallFirewallResponse>>(installFirewallRequest.await())
        } finally {
            controller.shutdown()
        }
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
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(id = "LOCAL-IP", version = 1),
                ),
            ),
        )
    }

    private class FakePortManagementSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakePortManagementSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakePortManagementSession(
                service = service,
                onInboundFrame = onInboundFrame,
            ).also { sessions += it }
        }

        fun requireLatestSession(service: RewriteService): FakePortManagementSession {
            return sessions.last { it.service == service }
        }

        fun requireLatestGameCommand() = requireLatestSession(RewriteService.GAME).sentFrames.last().command!!
    }

    private class FakePortManagementSession(
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
