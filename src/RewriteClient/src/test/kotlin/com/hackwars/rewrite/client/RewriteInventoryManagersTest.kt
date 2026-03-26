package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.systems.RewriteEquipmentManagerRow
import com.hackwars.rewrite.client.systems.RewriteFirewallManagerRow
import com.hackwars.rewrite.client.systems.allowEquipmentManagerFile
import com.hackwars.rewrite.client.systems.allowFirewallManagerFile
import com.hackwars.rewrite.client.systems.buildEquipmentManagerRows
import com.hackwars.rewrite.client.systems.buildFirewallManagerRows
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientEquipmentSlot
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHardwareState
import com.hackwars.rewrite.protocol.ClientInstallEquipmentPayload
import com.hackwars.rewrite.protocol.ClientInstallEquipmentResponse
import com.hackwars.rewrite.protocol.ClientInstalledEquipment
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
class RewriteInventoryManagersTest {
    @Test
    fun buildEquipmentManagerRowsReflectDecodedInstalledItemsAndFixedSlotOrder() {
        val rows = buildEquipmentManagerRows(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                hardware = ClientHardwareState(
                    equipmentSlots = mapOf(
                        "CPU" to ClientInstalledEquipment(
                            slot = "CPU",
                            name = "turbo-cpu.bin",
                            maker = "Maker A",
                            durability = 87,
                            cpuBoost = 12.0,
                            memoryBoost = 0,
                            storageBoost = 0,
                            watchCapacityBoost = 2,
                            healCostMultiplier = 0.5,
                            healModifierDelta = -2,
                            freezeImmune = true,
                            destroyWatchesImmune = false,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                ClientEquipmentSlot.CPU,
                ClientEquipmentSlot.MEMORY,
                ClientEquipmentSlot.STORAGE,
                ClientEquipmentSlot.PCI,
                ClientEquipmentSlot.AGP,
            ),
            rows.map(RewriteEquipmentManagerRow::slot),
        )
        assertEquals("turbo-cpu.bin", rows.first().equipmentLabel)
        assertEquals("Maker A", rows.first().maker)
        assertEquals(87, rows.first().durability)
        assertEquals("12", rows.first().cpuBoostDisplay)
        assertEquals("0.5", rows.first().healCostMultiplierDisplay)
        assertEquals("Click Here To Install Equipment", rows[1].equipmentLabel)
        assertEquals("-", rows[1].maker)
    }

    @Test
    fun buildFirewallManagerRowsReflectDecodedPortFlags() {
        val rows = buildFirewallManagerRows(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                ports = listOf(
                    ClientPortState(
                        number = 9,
                        enabled = false,
                        defaultPort = true,
                        dummy = true,
                        note = "Decoy",
                        installedFirewall = ClientInstalledFirewall(
                            name = "shield.fw",
                            kind = "BASIC",
                            maker = "Maker B",
                            strength = 6,
                            cpuCost = 1.5,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                RewriteFirewallManagerRow(
                    portNumber = 9,
                    firewallLabel = "shield.fw",
                    kind = "BASIC",
                    maker = "Maker B",
                    strength = 6,
                    cpuCost = 1.5,
                    enabled = false,
                    defaultPort = true,
                    dummy = true,
                    note = "Decoy",
                    installedFirewall = ClientInstalledFirewall(
                        name = "shield.fw",
                        kind = "BASIC",
                        maker = "Maker B",
                        strength = 6,
                        cpuCost = 1.5,
                    ),
                ),
            ),
            rows,
        )
        assertEquals("1.5", rows.single().cpuCostDisplay)
    }

    @Test
    fun chooserFiltersAllowMatchingEquipmentAndFirewallBinariesOnly() {
        val cpuEquipment = ClientStoredFile(
            path = "/Programs/cpu-card.bin",
            name = "cpu-card.bin",
            kind = ClientStoredFileKind.EQUIPMENT_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                equipmentSlot = ClientEquipmentSlot.CPU,
            ),
        )
        val memoryEquipment = ClientStoredFile(
            path = "/Programs/memory-card.bin",
            name = "memory-card.bin",
            kind = ClientStoredFileKind.EQUIPMENT_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(
                equipmentSlot = ClientEquipmentSlot.MEMORY,
            ),
        )
        val firewall = ClientStoredFile(
            path = "/Programs/guard.fw",
            name = "guard.fw",
            kind = ClientStoredFileKind.FIREWALL_BINARY,
            compiledBinary = ClientCompiledBinaryMetadata(),
        )
        val textFile = ClientStoredFile(
            path = "/Notes/readme.txt",
            name = "readme.txt",
            kind = ClientStoredFileKind.TEXT,
        )

        assertTrue(allowEquipmentManagerFile(cpuEquipment, ClientEquipmentSlot.CPU))
        assertFalse(allowEquipmentManagerFile(memoryEquipment, ClientEquipmentSlot.CPU))
        assertFalse(allowEquipmentManagerFile(textFile, ClientEquipmentSlot.CPU))
        assertTrue(allowFirewallManagerFile(firewall))
        assertFalse(allowFirewallManagerFile(cpuEquipment))
    }

    @Test
    fun controllerHelperSendsExpectedInstallEquipmentPayload() = runTest {
        val sessionGateway = FakeInventorySessionGateway()
        val controller = testController(
            sessionGateway = sessionGateway,
            scheduler = testScheduler,
        )
        acceptGameAuth(controller)

        try {
            val installPending = async {
                controller.requestInstallEquipment(
                    path = "/Programs",
                    name = "cpu-card.bin",
                    slot = ClientEquipmentSlot.CPU,
                )
            }
            runCurrent()

            val command = sessionGateway.requireLatestGameCommand()
            val payload = RewriteClientJson.decode(
                ClientInstallEquipmentPayload.serializer(),
                command.payload.toByteArray(),
            )
            assertEquals("installequipment", command.command_name)
            assertEquals("/Programs", payload.path)
            assertEquals("cpu-card.bin", payload.name)
            assertEquals(ClientEquipmentSlot.CPU, payload.slot)

            controller.accept(
                RewriteService.GAME,
                RewriteFrames.commandResponse(
                    commandId = command.command_id,
                    payload = RewriteClientJson.encode(
                        ClientInstallEquipmentResponse.serializer(),
                        ClientInstallEquipmentResponse(
                            stateId = "LOCAL-IP",
                            slot = ClientEquipmentSlot.CPU,
                            equipment = ClientInstalledEquipment(
                                slot = "CPU",
                                name = "cpu-card.bin",
                                maker = "Maker A",
                                durability = 100,
                                cpuBoost = 8.0,
                            ),
                            version = 2,
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
            assertIs<RewriteGameCommandResult.Success<ClientInstallEquipmentResponse>>(installPending.await())
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
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        version = 1,
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                    ),
                ),
            ),
        )
    }

    private class FakeInventorySessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeInventorySession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeInventorySession(service, onInboundFrame).also { sessions += it }
        }

        fun requireLatestGameCommand() = sessions.last { it.service == RewriteService.GAME }.sentFrames.last().command!!
    }

    private class FakeInventorySession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()
        private var closed = false

        override suspend fun send(frame: FrameEnvelope) {
            check(!closed)
            sentFrames += frame
        }

        override fun receive(frame: FrameEnvelope) {
            onInboundFrame(frame)
        }

        override fun close() {
            closed = true
        }
    }
}
