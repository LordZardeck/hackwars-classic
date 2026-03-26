package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.systems.RewriteWatchManagerRow
import com.hackwars.rewrite.client.systems.allowWatchManagerFile
import com.hackwars.rewrite.client.systems.buildWatchManagerRows
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientFetchWatchesPayload
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledWatch
import com.hackwars.rewrite.protocol.ClientInstallWatchPayload
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientSetWatchNotePayload
import com.hackwars.rewrite.protocol.ClientSetWatchOnOffPayload
import com.hackwars.rewrite.protocol.ClientSetWatchObservedPortsPayload
import com.hackwars.rewrite.protocol.ClientSetWatchQuantityPayload
import com.hackwars.rewrite.protocol.ClientSetWatchSearchFirewallPayload
import com.hackwars.rewrite.protocol.ClientChangeWatchPortPayload
import com.hackwars.rewrite.protocol.ClientChangeWatchTypePayload
import com.hackwars.rewrite.protocol.ClientDeleteWatchPayload
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientWatchKind
import com.hackwars.rewrite.protocol.ClientWatchListResponse
import com.hackwars.rewrite.protocol.ClientWatchManagerState
import com.hackwars.rewrite.protocol.ClientWatchMutationResponse
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
class RewriteWatchManagerTest {
    @Test
    fun buildWatchManagerRowsReflectDecodedStateAndScanRules() {
        val rows = buildWatchManagerRows(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                ports = listOf(
                    ClientPortState(number = 4),
                    ClientPortState(number = 6),
                    ClientPortState(number = 8),
                ),
                watches = ClientWatchManagerState(
                    watches = listOf(
                        ClientInstalledWatch(
                            kind = ClientWatchKind.HEALTH,
                            enabled = true,
                            note = "Guard",
                            cpuCost = 2.5,
                            quantityThreshold = 75.0,
                            installPort = 6,
                            observedPorts = listOf(6, 8),
                            searchFirewallType = 2,
                        ),
                        ClientInstalledWatch(
                            kind = ClientWatchKind.SCAN,
                            enabled = false,
                            note = "Scanner",
                            cpuCost = 1.0,
                            quantityThreshold = 0.0,
                            installPort = 4,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                RewriteWatchManagerRow(
                    index = 0,
                    type = ClientWatchKind.HEALTH,
                    enabled = true,
                    portChoices = listOf(4, 6, 8),
                    installPort = 6,
                    cpuCost = 2.5,
                    note = "Guard",
                    observedPorts = listOf(6, 8),
                    searchFirewallType = 2,
                    quantityThreshold = 75.0,
                    installedWatch = ClientInstalledWatch(
                        kind = ClientWatchKind.HEALTH,
                        enabled = true,
                        note = "Guard",
                        cpuCost = 2.5,
                        quantityThreshold = 75.0,
                        installPort = 6,
                        observedPorts = listOf(6, 8),
                        searchFirewallType = 2,
                    ),
                ),
                RewriteWatchManagerRow(
                    index = 1,
                    type = ClientWatchKind.SCAN,
                    enabled = false,
                    portChoices = emptyList(),
                    installPort = 4,
                    cpuCost = 1.0,
                    note = "Scanner",
                    observedPorts = emptyList(),
                    searchFirewallType = 0,
                    quantityThreshold = 0.0,
                    installedWatch = ClientInstalledWatch(
                        kind = ClientWatchKind.SCAN,
                        enabled = false,
                        note = "Scanner",
                        cpuCost = 1.0,
                        quantityThreshold = 0.0,
                        installPort = 4,
                    ),
                ),
            ),
            rows,
        )
        assertEquals("2.5", rows.first().cpuCostDisplay)
        assertEquals("Health", rows.first().typeLabel)
        assertTrue(rows[1].scanType)
    }

    @Test
    fun chooserFilterAllowsOnlyCompiledWatchBinaries() {
        val watchBinary = ClientStoredFile(
            path = "/Programs/watch.bin",
            name = "watch.bin",
            kind = ClientStoredFileKind.APPLICATION_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                scriptFamily = ClientScriptFamily.WATCH,
            ),
        )
        val attackBinary = ClientStoredFile(
            path = "/Programs/attack.bin",
            name = "attack.bin",
            kind = ClientStoredFileKind.APPLICATION_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                scriptFamily = ClientScriptFamily.ATTACK,
            ),
        )
        val textFile = ClientStoredFile(
            path = "/Notes/readme.txt",
            name = "readme.txt",
            kind = ClientStoredFileKind.TEXT,
        )

        assertTrue(allowWatchManagerFile(watchBinary))
        assertFalse(allowWatchManagerFile(attackBinary))
        assertFalse(allowWatchManagerFile(textFile))
    }

    @Test
    fun controllerHelpersSendExpectedWatchPayloads() = runTest {
        val sessionGateway = FakeWatchSessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        acceptGameAuth(controller)

        try {
            val fetchPending = async { controller.requestFetchWatches() }
            runCurrent()
            val fetchCommand = sessionGateway.requireLatestGameCommand()
            val fetchPayload = RewriteClientJson.decode(
                ClientFetchWatchesPayload.serializer(),
                fetchCommand.payload.toByteArray(),
            )
            assertEquals("fetchwatches", fetchCommand.command_name)
            assertEquals("192.0.2.10", fetchPayload.ip)
            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = fetchCommand.command_id,
                    payload = RewriteClientJson.encode(
                        ClientWatchListResponse.serializer(),
                        watchListResponse(),
                    ),
                ),
            )
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchListResponse>>(fetchPending.await())

            val installPending = async {
                controller.requestInstallWatch(
                    path = "/Programs",
                    name = "watch.bin",
                    type = 1,
                    portNumber = 8,
                )
            }
            runCurrent()
            val installCommand = sessionGateway.requireLatestGameCommand()
            val installPayload = RewriteClientJson.decode(
                ClientInstallWatchPayload.serializer(),
                installCommand.payload.toByteArray(),
            )
            assertEquals("installwatch", installCommand.command_name)
            assertEquals("/Programs", installPayload.path)
            assertEquals("watch.bin", installPayload.name)
            assertEquals(1, installPayload.type)
            assertEquals(8, installPayload.port)
            acceptWatchMutation(controller, installCommand.command_id, "installwatch")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(installPending.await())

            val notePending = async { controller.requestSetWatchNote(3, "Guard note") }
            runCurrent()
            val noteCommand = sessionGateway.requireLatestGameCommand()
            val notePayload = RewriteClientJson.decode(
                ClientSetWatchNotePayload.serializer(),
                noteCommand.payload.toByteArray(),
            )
            assertEquals("setwatchnote", noteCommand.command_name)
            assertEquals(3, notePayload.watchId)
            assertEquals("Guard note", notePayload.note)
            acceptWatchMutation(controller, noteCommand.command_id, "setwatchnote")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(notePending.await())

            val onOffPending = async { controller.requestSetWatchOnOff(2, true) }
            runCurrent()
            val onOffCommand = sessionGateway.requireLatestGameCommand()
            val onOffPayload = RewriteClientJson.decode(
                ClientSetWatchOnOffPayload.serializer(),
                onOffCommand.payload.toByteArray(),
            )
            assertEquals("setwatchonoff", onOffCommand.command_name)
            assertEquals(2, onOffPayload.watchId)
            assertEquals(true, onOffPayload.state)
            acceptWatchMutation(controller, onOffCommand.command_id, "setwatchonoff")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(onOffPending.await())

            val quantityPending = async { controller.requestSetWatchQuantity(2, 55.0) }
            runCurrent()
            val quantityCommand = sessionGateway.requireLatestGameCommand()
            val quantityPayload = RewriteClientJson.decode(
                ClientSetWatchQuantityPayload.serializer(),
                quantityCommand.payload.toByteArray(),
            )
            assertEquals("setwatchquantity", quantityCommand.command_name)
            assertEquals(2, quantityPayload.watchId)
            assertEquals(55.0, quantityPayload.quantity)
            acceptWatchMutation(controller, quantityCommand.command_id, "setwatchquantity")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(quantityPending.await())

            val observedPortsPending = async { controller.requestSetWatchObservedPorts(2, listOf(4, 6, 8)) }
            runCurrent()
            val observedPortsCommand = sessionGateway.requireLatestGameCommand()
            val observedPortsPayload = RewriteClientJson.decode(
                ClientSetWatchObservedPortsPayload.serializer(),
                observedPortsCommand.payload.toByteArray(),
            )
            assertEquals("setwatchobservedports", observedPortsCommand.command_name)
            assertEquals(2, observedPortsPayload.watchId)
            assertEquals(listOf(4, 6, 8), observedPortsPayload.observedPorts)
            acceptWatchMutation(controller, observedPortsCommand.command_id, "setwatchobservedports")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(observedPortsPending.await())

            val searchFirewallPending = async { controller.requestSetWatchSearchFirewall(2, 5) }
            runCurrent()
            val searchFirewallCommand = sessionGateway.requireLatestGameCommand()
            val searchFirewallPayload = RewriteClientJson.decode(
                ClientSetWatchSearchFirewallPayload.serializer(),
                searchFirewallCommand.payload.toByteArray(),
            )
            assertEquals("setwatchsearchfirewall", searchFirewallCommand.command_name)
            assertEquals(2, searchFirewallPayload.watchId)
            assertEquals(5, searchFirewallPayload.searchFirewall)
            acceptWatchMutation(controller, searchFirewallCommand.command_id, "setwatchsearchfirewall")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(searchFirewallPending.await())

            val changePortPending = async { controller.requestChangeWatchPort(2, 9) }
            runCurrent()
            val changePortCommand = sessionGateway.requireLatestGameCommand()
            val changePortPayload = RewriteClientJson.decode(
                ClientChangeWatchPortPayload.serializer(),
                changePortCommand.payload.toByteArray(),
            )
            assertEquals("changewatchport", changePortCommand.command_name)
            assertEquals(2, changePortPayload.watchId)
            assertEquals(9, changePortPayload.portId)
            acceptWatchMutation(controller, changePortCommand.command_id, "changewatchport")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(changePortPending.await())

            val changeTypePending = async { controller.requestChangeWatchType(2, 1) }
            runCurrent()
            val changeTypeCommand = sessionGateway.requireLatestGameCommand()
            val changeTypePayload = RewriteClientJson.decode(
                ClientChangeWatchTypePayload.serializer(),
                changeTypeCommand.payload.toByteArray(),
            )
            assertEquals("changewatchtype", changeTypeCommand.command_name)
            assertEquals(2, changeTypePayload.watchId)
            assertEquals(1, changeTypePayload.newType)
            acceptWatchMutation(controller, changeTypeCommand.command_id, "changewatchtype")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(changeTypePending.await())

            val deletePending = async { controller.requestDeleteWatch(2) }
            runCurrent()
            val deleteCommand = sessionGateway.requireLatestGameCommand()
            val deletePayload = RewriteClientJson.decode(
                ClientDeleteWatchPayload.serializer(),
                deleteCommand.payload.toByteArray(),
            )
            assertEquals("deletewatch", deleteCommand.command_name)
            assertEquals(2, deletePayload.watchId)
            acceptWatchMutation(controller, deleteCommand.command_id, "deletewatch")
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientWatchMutationResponse>>(deletePending.await())
        } finally {
            controller.shutdown()
        }
    }

    private fun acceptWatchMutation(
        controller: RewriteRootController,
        commandId: String,
        operation: String,
    ) {
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = commandId,
                payload = RewriteClientJson.encode(
                    ClientWatchMutationResponse.serializer(),
                    ClientWatchMutationResponse(
                        stateId = "192.0.2.10",
                        operation = operation,
                        accepted = true,
                        message = "$operation-succeeded",
                        snapshot = watchListResponse(),
                    ),
                ),
            ),
        )
    }

    private fun watchListResponse(): ClientWatchListResponse {
        return ClientWatchListResponse(
            stateId = "192.0.2.10",
            watches = listOf(
                ClientInstalledWatch(
                    kind = ClientWatchKind.HEALTH,
                    enabled = true,
                    note = "Guard",
                    cpuCost = 2.5,
                    quantityThreshold = 75.0,
                    installPort = 6,
                    observedPorts = listOf(6),
                ),
            ),
            installedCount = 1,
            maximumInstalledCount = 21,
            activeCount = 1,
            maximumActiveCount = 6,
            currentCpuLoad = 2.5,
            maximumCpuLoad = 25.0,
        )
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
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "192.0.2.10",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "192.0.2.10",
                        identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                    ),
                ),
            ),
        )
    }

    private class FakeWatchSessionGateway : RewriteServiceSessionGateway {
        private val sessions = linkedMapOf<RewriteService, FakeWatchSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeWatchSession(
                service = service,
                onInboundFrame = onInboundFrame,
            ).also { sessions[service] = it }
        }

        fun requireLatestGameCommand(): hackwars.rewrite.v1.CommandEnvelope {
            return sessions.getValue(RewriteService.GAME).sentFrames.last().command!!
        }
    }

    private class FakeWatchSession(
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
