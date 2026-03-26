package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.network.RewriteAttackBankingScriptSelection
import com.hackwars.rewrite.client.network.RewriteAttackPaneMode
import com.hackwars.rewrite.client.network.allowAttackChooserDirectory
import com.hackwars.rewrite.client.network.allowAttackChooserFile
import com.hackwars.rewrite.client.network.buildLegacyAttackExtraInfo
import com.hackwars.rewrite.client.network.buildLegacyAttackScripts
import com.hackwars.rewrite.client.network.clampRemoteFollowupPath
import com.hackwars.rewrite.client.network.deriveAttackSourcePortOptions
import com.hackwars.rewrite.client.network.reconcileAttackSourcePortSelection
import com.hackwars.rewrite.client.network.supportedShowChoicesActions
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientAttackCancelFailureCode
import com.hackwars.rewrite.protocol.ClientAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientAttackSessionKind
import com.hackwars.rewrite.protocol.ClientAttackStartResponse
import com.hackwars.rewrite.protocol.ClientAttackSessionState
import com.hackwars.rewrite.protocol.ClientChangeDailyPayOutcome
import com.hackwars.rewrite.protocol.ClientChangeDailyPayPayload
import com.hackwars.rewrite.protocol.ClientChangeDailyPayResponse
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientFloatHookValue
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledOutcome
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledPayload
import com.hackwars.rewrite.protocol.ClientFinalizeCancelledResponse
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientRequestAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestSecondaryDirectoryPayload
import com.hackwars.rewrite.protocol.ClientSecondaryDirectoryListingResponse
import com.hackwars.rewrite.protocol.ClientShowChoicesType
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientStringHookValue
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
class RewriteAttackWindowsTest {
    @Test
    fun sourcePortFilteringAndSelectionHonorLockedAttackRules() {
        val snapshot = ClientGameSnapshot(
            ports = listOf(
                ClientPortState(
                    number = 6,
                    enabled = true,
                    dummy = false,
                    note = "attack",
                    installedApplication = ClientInstalledApplication(name = "attack", kind = "ATTACK"),
                ),
                ClientPortState(
                    number = 7,
                    enabled = true,
                    dummy = false,
                    note = "redirect",
                    installedApplication = ClientInstalledApplication(name = "redirect", kind = "REDIRECT"),
                ),
                ClientPortState(
                    number = 8,
                    enabled = false,
                    dummy = false,
                    note = "off",
                    installedApplication = ClientInstalledApplication(name = "attack", kind = "ATTACK"),
                ),
                ClientPortState(
                    number = 9,
                    enabled = true,
                    dummy = true,
                    note = "dummy",
                    installedApplication = ClientInstalledApplication(name = "attack", kind = "ATTACK"),
                ),
            ),
        )

        val attackOptions = deriveAttackSourcePortOptions(snapshot, RewriteAttackPaneMode.ATTACK)
        val redirectOptions = deriveAttackSourcePortOptions(snapshot, RewriteAttackPaneMode.REDIRECT)

        assertEquals(listOf(6), attackOptions.map { it.portNumber })
        assertEquals(listOf(7), redirectOptions.map { it.portNumber })
        assertEquals(6, reconcileAttackSourcePortSelection(attackOptions, currentSelection = null, preferredPort = 6, defaultRedirectPort = null))
        assertEquals(7, reconcileAttackSourcePortSelection(redirectOptions, currentSelection = null, preferredPort = null, defaultRedirectPort = 7))
        assertNull(reconcileAttackSourcePortSelection(emptyList(), currentSelection = 6, preferredPort = null, defaultRedirectPort = null))
    }

    @Test
    fun legacyAttackLoadoutHelpersPreserveLockedWireShape() {
        val selection = RewriteAttackBankingScriptSelection(
            directoryPath = "/",
            fileName = "bank.bin",
            maliciousIp = "10.0.0.9",
            pettyCashTarget = 125.0,
        )

        val scripts = buildLegacyAttackScripts(selection)
        val extraInfo = buildLegacyAttackExtraInfo(selection, panePettyCashTarget = 50.0)

        assertEquals(4, scripts.size)
        assertEquals(listOf("/", "bank.bin"), scripts.first())
        assertEquals(listOf<String?>(null, null), scripts[1])
        assertIs<ClientStringHookValue>(extraInfo[0])
        assertEquals("10.0.0.9", (extraInfo[0] as ClientStringHookValue).value)
        assertIs<ClientFloatHookValue>(extraInfo[1])
        assertEquals(125.0, (extraInfo[1] as ClientFloatHookValue).value)
        assertIs<ClientFloatHookValue>(extraInfo[3])
        assertEquals(50.0, (extraInfo[3] as ClientFloatHookValue).value)
        assertFalse(allowAttackChooserDirectory(ClientDirectoryEntry(path = "/Store", name = "Store")))
        assertTrue(allowAttackChooserDirectory(ClientDirectoryEntry(path = "/Scripts", name = "Scripts")))
        assertTrue(
            allowAttackChooserFile(
                ClientStoredFile(
                    path = "/bank.bin",
                    name = "bank.bin",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.BANKING),
                ),
            ),
        )
        assertFalse(
            allowAttackChooserFile(
                ClientStoredFile(
                    path = "/attack.bin",
                    name = "attack.bin",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.ATTACK),
                ),
            ),
        )
    }

    @Test
    fun showChoicesMappingAndRemoteRootClampingHonorLockedRules() {
        assertEquals(
            listOf("Open Public FTP"),
            supportedShowChoicesActions(ClientShowChoicesType.FTP).map { it.label },
        )
        assertEquals(
            listOf("Open Store FTP"),
            supportedShowChoicesActions(ClientShowChoicesType.SHIPPING).map { it.label },
        )
        assertEquals(
            listOf("Change Daily Pay Target"),
            supportedShowChoicesActions(ClientShowChoicesType.HTTP).map { it.label },
        )
        assertTrue(supportedShowChoicesActions(ClientShowChoicesType.BANK).isEmpty())
        assertTrue(supportedShowChoicesActions(ClientShowChoicesType.ATTACK).isEmpty())

        assertEquals("/Public/docs", clampRemoteFollowupPath("/Public/docs", "/Public"))
        assertEquals("/Public", clampRemoteFollowupPath("/Public/../..", "/Public"))
        assertEquals("/Store", clampRemoteFollowupPath("/Secrets", "/Store"))
    }

    @Test
    fun controllerHelpersSendExpectedAttackPayloads() = runTest {
        val sessionGateway = FakeAttackSessionGateway()
        val controller = testController(sessionGateway = sessionGateway, scheduler = testScheduler)
        acceptGameAuth(controller)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                    ),
                ),
            ),
        )

        val attackPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestAttack(
                targetIp = "TARGET-IP",
                targetPort = 4,
                sourcePort = 6,
                secondaryPorts = listOf(9, 10),
                scripts = buildLegacyAttackScripts(
                    RewriteAttackBankingScriptSelection(
                        directoryPath = "/",
                        fileName = "bank.bin",
                        maliciousIp = "10.0.0.9",
                        pettyCashTarget = 125.0,
                    ),
                ),
                extraInfo = buildLegacyAttackExtraInfo(
                    bankingSelection = RewriteAttackBankingScriptSelection(
                        directoryPath = "/",
                        fileName = "bank.bin",
                        maliciousIp = "10.0.0.9",
                        pettyCashTarget = 125.0,
                    ),
                    panePettyCashTarget = 50.0,
                ),
                windowHandle = 44,
            )
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val attackCommand = session.sentFrames.last().command!!
        val attackPayload = RewriteClientJson.decode(
            ClientRequestAttackPayload.serializer(),
            attackCommand.payload.toByteArray(),
        )

        assertEquals("requestattack", attackCommand.command_name)
        assertEquals("LOCAL-IP", attackPayload.sourceIp)
        assertEquals("TARGET-IP", attackPayload.targetIp)
        assertEquals(6, attackPayload.sourcePort)
        assertEquals(listOf(9, 10), attackPayload.secondaryPorts)
        assertEquals(listOf("/", "bank.bin"), attackPayload.scripts.first())
        assertEquals(44, attackPayload.windowHandle)
        assertEquals("10.0.0.9", (attackPayload.extraInfo[0] as ClientStringHookValue).value)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = attackCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientAttackStartResponse.serializer(),
                    ClientAttackStartResponse(
                        attackerStateId = "LOCAL-IP",
                        sourcePort = 6,
                        targetStateId = "TARGET-IP",
                        targetPort = 4,
                        accepted = true,
                        message = "Attack accepted.",
                        session = ClientAttackSessionState(
                            programId = "attack-program-1",
                            sourcePort = 6,
                            targetStateId = "TARGET-IP",
                            targetPort = 4,
                            sessionKind = ClientAttackSessionKind.ATTACK,
                            windowHandle = 44,
                            secondaryPorts = listOf(9, 10),
                        ),
                        version = 2,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientAttackStartResponse>>(attackPending.await())

        val cancelPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestCancelAttack(6)
        }
        runCurrent()
        val cancelCommand = session.sentFrames.last().command!!
        val cancelPayload = RewriteClientJson.decode(
            ClientRequestCancelAttackPayload.serializer(),
            cancelCommand.payload.toByteArray(),
        )

        assertEquals("requestcancelattack", cancelCommand.command_name)
        assertEquals("LOCAL-IP", cancelPayload.ip)
        assertEquals(6, cancelPayload.port)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = cancelCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientAttackCancelResponse.serializer(),
                    ClientAttackCancelResponse(
                        stateId = "LOCAL-IP",
                        sourcePort = 6,
                        accepted = false,
                        failureCode = ClientAttackCancelFailureCode.SOURCE_IP_MISMATCH,
                        hadActiveSession = true,
                        message = "Source ip mismatch.",
                        version = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        val cancelResult = cancelPending.await()
        assertIs<RewriteGameCommandResult.Success<ClientAttackCancelResponse>>(cancelResult)
        assertFalse(cancelResult.value.accepted)
    }

    @Test
    fun controllerHelpersSendExpectedShowChoicesFollowupPayloads() = runTest {
        val sessionGateway = FakeAttackSessionGateway()
        val controller = testController(sessionGateway = sessionGateway, scheduler = testScheduler)
        acceptGameAuth(controller)

        val secondaryDirectoryPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestSecondaryDirectory(
                path = "/Public",
                targetIp = "TARGET-IP",
                portNumber = 25,
            )
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val secondaryDirectoryCommand = session.sentFrames.last().command!!
        val secondaryDirectoryPayload = RewriteClientJson.decode(
            ClientRequestSecondaryDirectoryPayload.serializer(),
            secondaryDirectoryCommand.payload.toByteArray(),
        )

        assertEquals("requestsecondarydirectory", secondaryDirectoryCommand.command_name)
        assertEquals("/Public", secondaryDirectoryPayload.path)
        assertEquals("TARGET-IP", secondaryDirectoryPayload.targetIp)
        assertEquals(25, secondaryDirectoryPayload.port)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = secondaryDirectoryCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientSecondaryDirectoryListingResponse.serializer(),
                    ClientSecondaryDirectoryListingResponse(
                        requesterStateId = "LOCAL-IP",
                        targetStateId = "TARGET-IP",
                        portNumber = 25,
                        path = "/Public",
                        version = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientSecondaryDirectoryListingResponse>>(secondaryDirectoryPending.await())

        val changeDailyPayPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestChangeDailyPay(
                targetIp = "TARGET-IP",
                targetPort = 25,
                revenueTargetIp = "REV-IP",
                attackPort = 44,
            )
        }
        runCurrent()

        val changeDailyPayCommand = session.sentFrames.last().command!!
        val changeDailyPayPayload = RewriteClientJson.decode(
            ClientChangeDailyPayPayload.serializer(),
            changeDailyPayCommand.payload.toByteArray(),
        )

        assertEquals("changedailypay", changeDailyPayCommand.command_name)
        assertEquals("TARGET-IP", changeDailyPayPayload.ip)
        assertEquals(25, changeDailyPayPayload.port)
        assertEquals("REV-IP", changeDailyPayPayload.change)
        assertEquals("LOCAL-IP", changeDailyPayPayload.finalizeIp)
        assertEquals(44, changeDailyPayPayload.attackPort)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = changeDailyPayCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientChangeDailyPayResponse.serializer(),
                    ClientChangeDailyPayResponse(
                        actorStateId = "LOCAL-IP",
                        targetStateId = "TARGET-IP",
                        targetPort = 25,
                        requestedRevenueTargetStateId = "REV-IP",
                        accepted = true,
                        outcome = ClientChangeDailyPayOutcome.SUCCESS,
                        message = "Daily pay successfully changed.",
                        reductionMultiplierAfter = 1.0,
                        revenueTargetStateIdAfter = "REV-IP",
                        requesterHttpExperienceAfter = 20.0,
                        actorVersion = 4,
                        targetVersion = 7,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientChangeDailyPayResponse>>(changeDailyPayPending.await())

        val finalizeCancelledPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestFinalizeCancelled(
                targetIp = "TARGET-IP",
                targetPort = 25,
            )
        }
        runCurrent()

        val finalizeCancelledCommand = session.sentFrames.last().command!!
        val finalizeCancelledPayload = RewriteClientJson.decode(
            ClientFinalizeCancelledPayload.serializer(),
            finalizeCancelledCommand.payload.toByteArray(),
        )

        assertEquals("finalizecancelled", finalizeCancelledCommand.command_name)
        assertEquals("LOCAL-IP", finalizeCancelledPayload.ip)
        assertEquals("TARGET-IP", finalizeCancelledPayload.targetIp)
        assertEquals(25, finalizeCancelledPayload.targetPort)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = finalizeCancelledCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientFinalizeCancelledResponse.serializer(),
                    ClientFinalizeCancelledResponse(
                        actorStateId = "LOCAL-IP",
                        targetStateId = "TARGET-IP",
                        targetPort = 25,
                        accepted = true,
                        outcome = ClientFinalizeCancelledOutcome.SUCCESS,
                        message = "finalizecancelled-succeeded",
                        targetVersion = 8,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientFinalizeCancelledResponse>>(finalizeCancelledPending.await())
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

    private class FakeAttackSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeAttackSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeAttackSession(service, onInboundFrame).also(sessions::add)
        }

        fun requireLatestGameSession(): FakeAttackSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeAttackSession(
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
