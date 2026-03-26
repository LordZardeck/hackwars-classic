package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.AttackCancelResponse
import com.hackwars.rewrite.gamecore.AttackMessageUiEvent
import com.hackwars.rewrite.gamecore.AttackPaneType
import com.hackwars.rewrite.gamecore.AttackSessionKind
import com.hackwars.rewrite.gamecore.AttackSessionState
import com.hackwars.rewrite.gamecore.AttackStartResponse
import com.hackwars.rewrite.gamecore.ChangeDailyPayPayload
import com.hackwars.rewrite.gamecore.ChangeDailyPayOutcome
import com.hackwars.rewrite.gamecore.ChangeDailyPayResponse
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.CombatMaintenanceProgramRegistry
import com.hackwars.rewrite.gamecore.CombatState
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.ComputerLogEntry
import com.hackwars.rewrite.gamecore.CoroutineProgramScheduler
import com.hackwars.rewrite.gamecore.DailyPayState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.EquipmentSlot
import com.hackwars.rewrite.gamecore.FilesystemState
import com.hackwars.rewrite.gamecore.FinalizeCancelledOutcome
import com.hackwars.rewrite.gamecore.FinalizeCancelledPayload
import com.hackwars.rewrite.gamecore.FinalizeCancelledResponse
import com.hackwars.rewrite.gamecore.FirewallCombatProfile
import com.hackwars.rewrite.gamecore.FirewallActionProfile
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.GameUiEvent
import com.hackwars.rewrite.gamecore.HealPortOutcome
import com.hackwars.rewrite.gamecore.HealPortPayload
import com.hackwars.rewrite.gamecore.HealPortResponse
import com.hackwars.rewrite.gamecore.HardwareState
import com.hackwars.rewrite.gamecore.InMemoryAttackProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.InstalledEquipment
import com.hackwars.rewrite.gamecore.InstalledFirewall
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.LogState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PopupUiEvent
import com.hackwars.rewrite.gamecore.PopupUiStyle
import com.hackwars.rewrite.gamecore.ProgramScriptBundle
import com.hackwars.rewrite.gamecore.ProgramScriptSlot
import com.hackwars.rewrite.gamecore.ProgramLifecycleStatus
import com.hackwars.rewrite.gamecore.RequestAttackPayload
import com.hackwars.rewrite.gamecore.RequestCancelAttackPayload
import com.hackwars.rewrite.gamecore.RequestZombieAttackPayload
import com.hackwars.rewrite.gamecore.RequestZombieCancelAttackPayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.RuntimeState
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.ShowChoicesType
import com.hackwars.rewrite.gamecore.ShowChoicesUiEvent
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.TextMessageUiEvent
import com.hackwars.rewrite.gamecore.WeakenedPortAccessState
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WebsiteState
import com.hackwars.rewrite.gamecore.ZombieAttackCancelResponse
import com.hackwars.rewrite.gamecore.ZombieAttackStartFailureCode
import com.hackwars.rewrite.gamecore.ZombieAttackStartResponse
import com.hackwars.rewrite.gamecore.ZombieAttackUiEvent
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.FrameEnvelope
import hackwars.rewrite.v1.ProgramStatus
import hackwars.rewrite.v1.CommandResponseStatus
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameAttackProtocolAdapterTest {
    @Test
    fun requestAttackPublishesBilateralDeltasThenResponseAndScopesProgramUpdatesToTheAttacker() = runTest {
        val fixture = createFixture()
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        secondaryPorts = listOf(7, 8),
                        windowHandle = 4,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val delta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = AttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy", "ports", "combat", "runtime"), delta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
        assertEquals(12, response.sourcePort)
        assertEquals("TARGET-IP", response.targetStateId?.value)
        assertEquals(listOf(25, 7, 8), response.session?.targetCyclePorts)
        assertEquals(0, response.session?.targetCycleCursor)
        assertEquals("TARGET-IP", response.session?.targetView?.targetStateId?.value)
        assertEquals(25, response.session?.targetView?.targetPort)
        assertEquals(100.0, response.session?.targetView?.health)

        runCurrent()

        val updateFrame = attacker.awaitFrame()
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun requestAttackStartsRedirectSessionsOnTheExistingWireAndPublishesRedirectProgramUpdates() = runTest {
        val fixture = createFixture(
            localState = redirectAttackerState(GameStateId("LOCAL-IP")),
            targetState = redirectTargetState(GameStateId("TARGET-IP")),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "redirect-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val attackerDelta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = AttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertTrue(response.accepted)
        assertEquals(AttackSessionKind.REDIRECT, response.session?.sessionKind)
        assertEquals("redirect-started", response.message)
        assertEquals(setOf("economy", "ports", "combat", "runtime"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())

        runCurrent()

        val updateFrame = attacker.awaitFrame()
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertEquals("redirect", updateFrame.program_update?.program_type)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun redirectStartFailurePublishesPopupBeforeItsCorrelatedResponse() = runTest {
        val baseLocal = redirectAttackerState(GameStateId("LOCAL-IP"))
        val fixture = createFixture(
            localState = baseLocal.copy(
                economy = baseLocal.economy.copy(pettyCash = 5.0),
            ),
            targetState = redirectTargetState(GameStateId("TARGET-IP")),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "redirect-fail-cash-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        windowHandle = 4,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val popupFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val popup = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = popupFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = AttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        val popupEvent = assertIs<PopupUiEvent>(popup)
        assertEquals("popup", popupFrame.game_ui_event?.event_type)
        assertEquals("You must have \$10 in your petty cash to start a redirect.", popupEvent.message)
        assertEquals(PopupUiStyle.ERROR, popupEvent.style)
        assertFalse(response.accepted)
        assertEquals("You must have \$10 in your petty cash to start a redirect.", response.message)
    }

    @Test
    fun redirectCompletionPublishesPaneTypedAttackMessagesWithWindowHandleBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = redirectAttackerState(
                GameStateId("LOCAL-IP"),
                redirectScriptBundle = redirectScriptBundle(
                    continueScript = """int main() { redirectSilicon(); return 0; }""",
                ),
            ),
            targetState = redirectTargetState(
                stateId = GameStateId("TARGET-IP"),
                health = 2.0,
                isNpc = true,
                commodities = listOf(0.0, 0.0, 50.0, 0.0, 0.0),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "redirect-complete-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        windowHandle = 4,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetFrames = target.drainFrames()
        val attackerFrames = attacker.drainFrames()
        val targetDelta = targetFrames.first { it.delta != null }
        val attackerDelta = attackerFrames.first { it.delta != null }
        val uiFrames = attackerFrames.filter { it.game_ui_event != null }
        assertEquals(4, uiFrames.size)
        val receiptPaneFrame = uiFrames[0]
        val receiptTextFrame = uiFrames[1]
        val finishedPaneFrame = uiFrames[2]
        val finishedTextFrame = uiFrames[3]
        val completedFrame = attackerFrames.last { it.program_update != null }

        val receiptPane = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = receiptPaneFrame.game_ui_event!!.payload.toByteArray(),
        )
        val receiptText = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = receiptTextFrame.game_ui_event!!.payload.toByteArray(),
        )
        val finishedPane = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = finishedPaneFrame.game_ui_event!!.payload.toByteArray(),
        )
        val finishedText = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = finishedTextFrame.game_ui_event!!.payload.toByteArray(),
        )

        assertEquals("TARGET-IP", targetDelta.delta?.game_state_id)
        assertEquals("LOCAL-IP", attackerDelta.delta?.game_state_id)
        assertEquals("attack_message", receiptPaneFrame.game_ui_event?.event_type)
        val receiptPaneEvent = assertIs<AttackMessageUiEvent>(receiptPane)
        assertEquals(AttackPaneType.REDIRECT, receiptPaneEvent.paneType)
        assertEquals(4, receiptPaneEvent.windowHandle)
        assertEquals("Received 50 Silicon.", receiptPaneEvent.message)
        assertEquals("message", receiptTextFrame.game_ui_event?.event_type)
        assertEquals("Received 50 Silicon from TARGET-IP.", assertIs<TextMessageUiEvent>(receiptText).message)
        val finishedPaneEvent = assertIs<AttackMessageUiEvent>(finishedPane)
        assertEquals("attack_message", finishedPaneFrame.game_ui_event?.event_type)
        assertEquals(AttackPaneType.REDIRECT, finishedPaneEvent.paneType)
        assertEquals(4, finishedPaneEvent.windowHandle)
        assertEquals("Finished redirecting.", finishedPaneEvent.message)
        assertEquals("Port 12 finished redirecting.", assertIs<TextMessageUiEvent>(finishedText).message)
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, completedFrame.program_update?.status)
        assertEquals("redirect", completedFrame.program_update?.program_type)
    }

    @Test
    fun initializeScriptLogsFlushBeforeTheCorrelatedStartResponse() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    initialize = """int main() { logMessage("init"); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-init-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val attackerDelta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = AttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy", "ports", "combat", "runtime", "logs"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
    }

    @Test
    fun initializeMessageEventFlushesAfterStartDeltasAndBeforeTheCorrelatedResponse() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    initialize = """int main() { message("LOCAL-IP", "hello-self"); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-init-message-1",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val attackerDelta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val messageFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val message = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = messageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = AttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy", "ports", "combat", "runtime"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals("message", messageFrame.game_ui_event?.event_type)
        assertEquals("hello-self", assertIs<TextMessageUiEvent>(message).message)
        assertTrue(response.accepted)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun healPortPublishesLocalDeltasBeforeItsCorrelatedResponse() = runTest {
        val baseLocal = attackerState(GameStateId("LOCAL-IP"))
        val fixture = createFixture(
            localState = baseLocal.copy(
                hardware = baseLocal.hardware.copy(
                    equipmentSlots = mapOf(
                        EquipmentSlot.CPU to InstalledEquipment(
                            slot = EquipmentSlot.CPU,
                            name = "healer.bin",
                            healCostMultiplier = 0.5,
                        ),
                    ),
                ),
                ports = baseLocal.ports + PortState(
                    number = 22,
                    type = "ssh",
                    enabled = true,
                    health = 80.0,
                ),
                watches = WatchManagerState(
                    watches = listOf(
                        InstalledWatch(
                            kind = WatchKind.HEALTH,
                            enabled = true,
                            note = "heal-watch",
                            cpuCost = 2.0,
                            quantityThreshold = 50.0,
                            baselineQuantity = 80.0,
                            installPort = 22,
                            searchFirewallType = 0,
                            observedPorts = listOf(22),
                            contents = "watch",
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.WATCH,
                                applicationKind = ApplicationKind.WATCH,
                                outputName = "watch.bin",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "healport-1",
                commandName = "healport",
                payload = RewriteGameJson.encode(
                    serializer = HealPortPayload.serializer(),
                    value = HealPortPayload(
                        ip = "LOCAL-IP",
                        port = 22,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val deltaFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = HealPortResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy", "ports", "watches"), deltaFrame.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
        assertEquals(HealPortOutcome.SUCCESS, response.outcome)
        assertEquals(20.0, response.chargedAmount)
        assertTrue(attacker.drainFrames().isEmpty())
    }

    @Test
    fun changeDailyPayPublishesDeltasThenUiEventsBeforeItsCorrelatedResponse() = runTest {
        val fixture = createFixture(
            targetState = targetState(
                GameStateId("TARGET-IP"),
                installedApplication = InstalledApplication(
                    name = "site.bin",
                    kind = ApplicationKind.HTTP,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "changedailypay-1",
                commandName = "changedailypay",
                payload = RewriteGameJson.encode(
                    serializer = ChangeDailyPayPayload.serializer(),
                    value = ChangeDailyPayPayload(
                        ip = "TARGET-IP",
                        port = 25,
                        change = "REV-IP",
                        finalizeIp = "LOCAL-IP",
                        attackPort = 0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val actorDelta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val attackMessageFrame = attacker.awaitFrame()
        val textMessageFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val attackMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = attackMessageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val textMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = textMessageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = ChangeDailyPayResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("stats"), actorDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("dailyPay"), targetDelta.delta?.delta_keys?.toSet())
        val attackUi = assertIs<AttackMessageUiEvent>(attackMessage)
        assertEquals("attack_message", attackMessageFrame.game_ui_event?.event_type)
        assertEquals("Daily pay successfully changed.", attackUi.message)
        assertEquals(25, attackUi.port)
        assertEquals("TARGET-IP", attackUi.ip)
        assertEquals("message", textMessageFrame.game_ui_event?.event_type)
        assertEquals("Daily pay successfully changed.", assertIs<TextMessageUiEvent>(textMessage).message)
        assertTrue(response.accepted)
        assertEquals(ChangeDailyPayOutcome.SUCCESS, response.outcome)
        assertEquals(GameStateId("REV-IP"), response.revenueTargetStateIdAfter)
        assertEquals(20.0, response.requesterHttpExperienceAfter)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun changeDailyPayFirewallNoopPublishesPopupMessageBeforeItsCorrelatedResponse() = runTest {
        val fixture = createFixture(
            targetState = targetState(
                GameStateId("TARGET-IP"),
                installedApplication = InstalledApplication(
                    name = "site.bin",
                    kind = ApplicationKind.HTTP,
                ),
                firewallActionProfile = FirewallActionProfile(
                    changeDailyPayFailChance = 1.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "changedailypay-firewall-1",
                commandName = "changedailypay",
                payload = RewriteGameJson.encode(
                    serializer = ChangeDailyPayPayload.serializer(),
                    value = ChangeDailyPayPayload(
                        ip = "TARGET-IP",
                        port = 25,
                        change = "REV-IP",
                        finalizeIp = "LOCAL-IP",
                        attackPort = 0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val popupFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val popup = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = popupFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = ChangeDailyPayResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("popup", popupFrame.game_ui_event?.event_type)
        val popupEvent = assertIs<PopupUiEvent>(popup)
        assertEquals("TARGET-IP's firewall has caused the change daily pay to fail.", popupEvent.message)
        assertEquals(PopupUiStyle.MESSAGE, popupEvent.style)
        assertTrue(response.accepted)
        assertEquals(ChangeDailyPayOutcome.FIREWALL_NOOP, response.outcome)
        assertTrue(attacker.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun changeDailyPayInvalidTargetPortReturnsAnErrorWithoutUiEvents() = runTest {
        val fixture = createFixture()
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "changedailypay-invalid-port-1",
                commandName = "changedailypay",
                payload = RewriteGameJson.encode(
                    serializer = ChangeDailyPayPayload.serializer(),
                    value = ChangeDailyPayPayload(
                        ip = "TARGET-IP",
                        port = 999,
                        change = "REV-IP",
                        finalizeIp = "LOCAL-IP",
                        attackPort = 0,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val responseFrame = attacker.awaitFrame()

        assertEquals(CommandResponseStatus.COMMAND_RESPONSE_STATUS_ERROR, responseFrame.command_response?.status)
        assertTrue(attacker.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeCancelledPublishesTargetCleanupDeltasBeforeItsCorrelatedResponse() = runTest {
        val fixture = createFixture(
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 0.0,
                installedApplication = InstalledApplication(
                    name = "site.bin",
                    kind = ApplicationKind.HTTP,
                    cpuCost = 2.0,
                ),
            ).copy(
                ports = listOf(
                    PortState(
                        number = 25,
                        type = "http",
                        enabled = true,
                        health = 0.0,
                        healCount = 4,
                        weakenedAccess = WeakenedPortAccessState(
                            actorStateId = GameStateId("LOCAL-IP"),
                            grantedAtEpochMillis = 5_000L,
                        ),
                        installedApplication = InstalledApplication(
                            name = "site.bin",
                            kind = ApplicationKind.HTTP,
                            cpuCost = 2.0,
                        ),
                    ),
                ),
                watches = WatchManagerState(
                    watches = listOf(
                        InstalledWatch(
                            kind = WatchKind.HEALTH,
                            enabled = true,
                            note = "cleanup-watch",
                            cpuCost = 2.0,
                            quantityThreshold = 50.0,
                            baselineQuantity = 0.0,
                            installPort = 25,
                            searchFirewallType = 0,
                            observedPorts = listOf(25),
                            contents = "watch",
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = ScriptFamily.WATCH,
                                applicationKind = ApplicationKind.WATCH,
                                outputName = "watch.bin",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "finalizecancelled-1",
                commandName = "finalizecancelled",
                payload = RewriteGameJson.encode(
                    serializer = FinalizeCancelledPayload.serializer(),
                    value = FinalizeCancelledPayload(
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val targetDelta = target.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = FinalizeCancelledResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val updatedTarget = requireNotNull(fixture.repository.load(GameStateId("TARGET-IP")))

        assertEquals(setOf("ports", "watches"), targetDelta.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
        assertEquals(FinalizeCancelledOutcome.SUCCESS, response.outcome)
        assertEquals(100.0, updatedTarget.ports.first { it.number == 25 }.health)
        assertNull(updatedTarget.ports.first { it.number == 25 }.weakenedAccess)
        assertTrue(attacker.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun sessionEndCancelsTheHiddenDailyIncomeRuntimeWhenTheLastSubscriberDisconnects() = runTest {
        val localId = GameStateId("LOCAL-IP")
        val baseLocal = attackerState(localId)
        val fixture = createFixture(
            localState = baseLocal.copy(
                website = WebsiteState(votesAvailable = 0),
                dailyPay = DailyPayState(
                    revenueTargetStateId = localId,
                    lastPaidAtEpochMillis = 5_000L,
                ),
                ports = baseLocal.ports + PortState(
                    number = 80,
                    type = "http",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "site.bin",
                        kind = ApplicationKind.HTTP,
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val beforeClose = requireNotNull(fixture.repository.load(localId))

        attacker.connection.close()
        runCurrent()
        advanceTimeBy(43_261_000L)
        runCurrent()

        val updated = requireNotNull(fixture.repository.load(localId))
        assertEquals(beforeClose.website.votesAvailable, updated.website.votesAvailable)
        assertEquals(beforeClose.economy.pettyCash, updated.economy.pettyCash)
        assertEquals(beforeClose.economy.bankMoney, updated.economy.bankMoney)
        assertEquals(beforeClose.dailyPay.lastPaidAtEpochMillis, updated.dailyPay.lastPaidAtEpochMillis)
    }

    @Test
    fun attackTicksPublishTargetAndAttackerDeltasBeforeAttackerScopedRunningUpdate() = runTest {
        val fixture = createFixture()
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-tick-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun continueChangeDailyPayPublishesUiEventsAfterTickDeltasAndBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { changeDailyPay("REV-IP"); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                installedApplication = InstalledApplication(
                    name = "site.bin",
                    kind = ApplicationKind.HTTP,
                ),
                firewallActionProfile = FirewallActionProfile(
                    changeDailyPayReductionMultiplier = 0.3,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-continue-change-pay-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val attackMessageFrame = attacker.awaitFrame()
        val textMessageFrame = attacker.awaitFrame()
        val cancelledFrame = attacker.awaitFrame()
        val attackMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = attackMessageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val textMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = textMessageFrame.game_ui_event!!.payload.toByteArray(),
        )

        assertEquals("TARGET-IP", targetDelta.delta?.game_state_id)
        assertEquals("LOCAL-IP", attackerDelta.delta?.game_state_id)
        assertEquals("attack_message", attackMessageFrame.game_ui_event?.event_type)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<AttackMessageUiEvent>(attackMessage).message,
        )
        assertEquals("message", textMessageFrame.game_ui_event?.event_type)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<TextMessageUiEvent>(textMessage).message,
        )
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, cancelledFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeChangeDailyPayPublishesUiEventsAfterCompletionDeltasAndBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { changeDailyPay("REV-IP"); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                installedApplication = InstalledApplication(
                    name = "site.bin",
                    kind = ApplicationKind.HTTP,
                ),
                firewallActionProfile = FirewallActionProfile(
                    changeDailyPayReductionMultiplier = 0.3,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-change-pay-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val attackMessageFrame = attacker.awaitFrame()
        val textMessageFrame = attacker.awaitFrame()
        val completedFrame = attacker.awaitFrame()
        val attackMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = attackMessageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val textMessage = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = textMessageFrame.game_ui_event!!.payload.toByteArray(),
        )

        assertEquals("TARGET-IP", targetDelta.delta?.game_state_id)
        assertEquals("LOCAL-IP", attackerDelta.delta?.game_state_id)
        assertEquals("attack_message", attackMessageFrame.game_ui_event?.event_type)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<AttackMessageUiEvent>(attackMessage).message,
        )
        assertEquals("message", textMessageFrame.game_ui_event?.event_type)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<TextMessageUiEvent>(textMessage).message,
        )
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, completedFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun continueScriptLogDeltasFlushBeforeTheAttackerScopedRunningUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { logMessage("continue"); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-continue-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats", "logs"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
    }

    @Test
    fun continueMessageEventFlushesAfterTickDeltasAndBeforeAttackerScopedRunningUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { message("TARGET-IP", "tick-message"); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-continue-message-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val messageFrame = target.awaitFrame()
        val message = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = messageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals("message", messageFrame.game_ui_event?.event_type)
        assertEquals("tick-message", assertIs<TextMessageUiEvent>(message).message)
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().none { it.program_update != null })
    }

    @Test
    fun continueMessageThenCancelStillDeliversTargetedEventBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { message("TARGET-IP", "retreat"); cancelAttack(); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-continue-message-cancel-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val messageFrame = target.awaitFrame()
        val message = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = messageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals("message", messageFrame.game_ui_event?.event_type)
        assertEquals("retreat", assertIs<TextMessageUiEvent>(message).message)
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().none { it.program_update != null })
    }

    @Test
    fun continueShowChoicesSerializesTypedUiEventBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { showChoices(); cancelAttack(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                installedApplication = InstalledApplication(
                    name = "target-http.bin",
                    kind = ApplicationKind.HTTP,
                    cpuCost = 2.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-continue-show-choices-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val showChoicesFrame = attacker.awaitFrame()
        val showChoices = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = showChoicesFrame.game_ui_event!!.payload.toByteArray(),
        )
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals("show_choices", showChoicesFrame.game_ui_event?.event_type)
        val event = assertIs<ShowChoicesUiEvent>(showChoices)
        assertEquals("TARGET-IP", event.targetIp)
        assertEquals(25, event.targetPort)
        assertEquals(ShowChoicesType.HTTP, event.choiceType)
        assertEquals(0, event.windowHandle)
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().none { it.game_ui_event != null || it.program_update != null })
    }

    @Test
    fun emptyPettyCashContinueFlushesAttackerAndTargetEconomyDeltasBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { emptyPettyCash(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                pettyCash = 80.0,
                portType = "bank",
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                ),
                firewallActionProfile = FirewallActionProfile(
                    emptyPettyCashReductionMultiplier = 0.5,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-empty-petty-cash-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("economy", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("economy", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun emptyPettyCashFirewallFailStaysZeroAmountAndStillCancelsWithoutEconomyDeltas() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { emptyPettyCash(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                pettyCash = 80.0,
                portType = "bank",
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                ),
                firewallActionProfile = FirewallActionProfile(
                    emptyPettyCashFailChance = 0.25,
                    emptyPettyCashReductionMultiplier = 0.5,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-empty-petty-cash-fail-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeEmptyPettyCashFlushesEconomyDeltasBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { emptyPettyCash(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                pettyCash = 50.0,
                portType = "bank",
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-empty-petty-cash-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("economy", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("economy", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun stealFileContinueFlushesAttackerAndTargetFilesystemDeltasBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { stealFile(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                portType = "ftp",
                installedApplication = InstalledApplication(
                    name = "target-ftp.bin",
                    kind = ApplicationKind.FTP,
                    cpuCost = 2.0,
                ),
                filesystemFiles = listOf(
                    storedFile("/Public", "loot.txt"),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-steal-file-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("filesystem", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun stealFileFirewallFailStaysNoTransferAndStillCancelsWithoutFilesystemDeltas() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { stealFile(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                portType = "ftp",
                installedApplication = InstalledApplication(
                    name = "target-ftp.bin",
                    kind = ApplicationKind.FTP,
                    cpuCost = 2.0,
                ),
                firewallActionProfile = FirewallActionProfile(
                    stealFileFailChance = 0.25,
                ),
                filesystemFiles = listOf(
                    storedFile("/Public", "loot.txt"),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-steal-file-fail-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeStealFileFlushesFilesystemDeltasBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { stealFile(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                portType = "ftp",
                installedApplication = InstalledApplication(
                    name = "target-ftp.bin",
                    kind = ApplicationKind.FTP,
                    cpuCost = 2.0,
                ),
                filesystemFiles = listOf(
                    storedFile("/Public", "loot.txt"),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-steal-file-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("filesystem", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun continueInstallScriptConsumesAttackerBinaryBeforeCancelledUpdateWithoutTargetInstallOnNonWeakenedPort() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { installScript(); return 0; }""",
                ),
                filesystemFiles = listOf(
                    storedFile(
                        "/Public",
                        "worm.bin",
                        kind = com.hackwars.rewrite.gamecore.StoredFileKind.APPLICATION_BINARY,
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.BANKING,
                            applicationKind = ApplicationKind.BANKING,
                            outputName = "worm.bin",
                        ),
                        scriptBundle = ProgramScriptBundle(
                            family = ScriptFamily.BANKING,
                            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "infected"),
                        ),
                    ),
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                portType = "bank",
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-install-script-continue-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        scripts = listOf(listOf("/Public", "worm.bin")),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()
        val updatedAttacker = requireNotNull(fixture.repository.load(GameStateId("LOCAL-IP")))
        val updatedTarget = requireNotNull(fixture.repository.load(GameStateId("TARGET-IP")))

        assertNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(97.8, updatedTarget.ports.first { it.number == 25 }.health)
        assertNull(updatedTarget.ports.first { it.number == 25 }.installedApplication?.scriptBundle)
        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeInstallScriptFlushesAttackerFilesystemAndTargetPortMutationBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { installScript(); return 0; }""",
                ),
                filesystemFiles = listOf(
                    storedFile(
                        "/Public",
                        "worm.bin",
                        kind = com.hackwars.rewrite.gamecore.StoredFileKind.APPLICATION_BINARY,
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.BANKING,
                            applicationKind = ApplicationKind.BANKING,
                            outputName = "worm.bin",
                        ),
                        scriptBundle = ProgramScriptBundle(
                            family = ScriptFamily.BANKING,
                            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "infected"),
                        ),
                    ),
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                portType = "bank",
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-install-script-finalize-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        scripts = listOf(listOf("/Public", "worm.bin")),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()
        val updatedAttacker = requireNotNull(fixture.repository.load(GameStateId("LOCAL-IP")))
        val updatedTarget = requireNotNull(fixture.repository.load(GameStateId("TARGET-IP")))

        assertNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals(100.0, updatedTarget.ports.first { it.number == 25 }.health)
        assertEquals("infected", updatedTarget.ports.first { it.number == 25 }.installedApplication?.scriptBundle?.script(ProgramScriptSlot.DEPOSIT))
        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeInstallScriptFirewallFailConsumesAttackerBinaryWithoutTargetInstallMutation() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { installScript(); return 0; }""",
                ),
                filesystemFiles = listOf(
                    storedFile(
                        "/Public",
                        "worm.bin",
                        kind = com.hackwars.rewrite.gamecore.StoredFileKind.APPLICATION_BINARY,
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.BANKING,
                            applicationKind = ApplicationKind.BANKING,
                            outputName = "worm.bin",
                        ),
                        scriptBundle = ProgramScriptBundle(
                            family = ScriptFamily.BANKING,
                            scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "infected"),
                        ),
                    ),
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                portType = "bank",
                firewallActionProfile = FirewallActionProfile(
                    installScriptFailChance = 0.25,
                ),
                installedApplication = InstalledApplication(
                    name = "target-bank.bin",
                    kind = ApplicationKind.BANKING,
                    banking = true,
                    cpuCost = 2.0,
                    scriptBundle = ProgramScriptBundle(
                        family = ScriptFamily.BANKING,
                        scriptsBySlot = linkedMapOf(ProgramScriptSlot.DEPOSIT to "clean"),
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-install-script-firewall-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        scripts = listOf(listOf("/Public", "worm.bin")),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()
        val updatedAttacker = requireNotNull(fixture.repository.load(GameStateId("LOCAL-IP")))
        val updatedTarget = requireNotNull(fixture.repository.load(GameStateId("TARGET-IP")))

        assertNull(updatedAttacker.filesystem.resolveFile("/Public", "worm.bin"))
        assertEquals("clean", updatedTarget.ports.first { it.number == 25 }.installedApplication?.scriptBundle?.script(ProgramScriptSlot.DEPOSIT))
        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("filesystem", "combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun freezeTicksFlushFrozenPortDeltaBeforeTheAttackerScopedRunningUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { freeze(); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-freeze-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun firewallAttackBackFlushesAttackerAndTargetPortDeltasBeforeRunningUpdate() = runTest {
        val fixture = createFixture(
            targetState = targetState(
                GameStateId("TARGET-IP"),
                firewallCombatProfile = FirewallCombatProfile(
                    httpDamageModifier = 0.5,
                    attackBackDamage = 3.0,
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-firewall-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats", "ports"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun switchAttackTicksFlushRetargetedCombatAndNewTargetPortDeltaBeforeRunningUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { switchAttack(); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                additionalPorts = listOf(
                    PortState(number = 26, type = "ftp", enabled = false, health = 100.0),
                    PortState(number = 27, type = "bank", enabled = true, health = 100.0),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-switch-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                        secondaryPorts = listOf(26, 27),
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun berserkTicksFlushAttackerAndTargetDamageBeforeRunningOrCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { berserk(); return 0; }""",
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-berserk-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats", "ports"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun continueDeleteLogsFlushesTargetLogAndCleanupDeltasBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                logs = listOf(
                    ComputerLogEntry(1L, "delete-me", "REMOTE-IP"),
                    ComputerLogEntry(2L, "keep-me", "OTHER-IP"),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-delete-logs-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("logs", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun continueDestroyWatchesFlushesTargetWatchAndRuntimeDeltasBeforeCancelledUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    continueScript = """int main() { destroyWatches(); return 0; }""",
                ),
            ),
            targetState = targetState(GameStateId("TARGET-IP")).copy(
                runtime = RuntimeState(currentCpuLoad = 7.0),
                watches = WatchManagerState(
                    watches = listOf(
                        targetWatch(note = "remove-health", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 3.0),
                        targetWatch(note = "remove-cash", kind = WatchKind.PETTY_CASH, installPort = 25, cpuCost = 2.0),
                        targetWatch(note = "keep-scan", kind = WatchKind.SCAN, installPort = 25, cpuCost = 2.0),
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-destroy-watches-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("watches", "runtime", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun attackCompletionPublishesBilateralCleanupAndCompletedProgramUpdate() = runTest {
        val fixture = createFixture(
            targetState = targetState(GameStateId("TARGET-IP"), health = 1.5),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-complete-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeDestroyWatchesFlushesTargetWatchAndRuntimeDeltasBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { destroyWatches(); return 0; }""",
                ),
            ),
            targetState = targetState(GameStateId("TARGET-IP"), health = 1.5).copy(
                runtime = RuntimeState(currentCpuLoad = 6.0),
                watches = WatchManagerState(
                    watches = listOf(
                        targetWatch(note = "remove-health", kind = WatchKind.HEALTH, installPort = 25, cpuCost = 4.0),
                        targetWatch(note = "keep-scan", kind = WatchKind.SCAN, installPort = 25, cpuCost = 2.0),
                    ),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-destroy-watches-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("watches", "runtime", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeDeleteLogsFlushesTargetLogDeltaBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
                ),
            ),
            targetState = targetState(
                GameStateId("TARGET-IP"),
                health = 1.5,
                logs = listOf(
                    ComputerLogEntry(1L, "remove", "REMOTE-IP"),
                    ComputerLogEntry(2L, "keep", "OTHER-IP"),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-delete-logs-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("logs", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun finalizeScriptLogDeltasFlushBeforeTheCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { logMessage("finalize"); return 0; }""",
                ),
            ),
            targetState = targetState(GameStateId("TARGET-IP"), health = 1.5),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats", "logs"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
    }

    @Test
    fun finalizeMessageEventFlushesAfterCompletionDeltasAndBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = attackScriptBundle(
                    finalize = """int main() { message("TARGET-IP", "finished"); return 0; }""",
                ),
            ),
            targetState = targetState(GameStateId("TARGET-IP"), health = 1.5),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-finalize-message-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val messageFrame = target.awaitFrame()
        val message = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = messageFrame.game_ui_event!!.payload.toByteArray(),
        )
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals("message", messageFrame.game_ui_event?.event_type)
        assertEquals("finished", assertIs<TextMessageUiEvent>(message).message)
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().none { it.program_update != null })
    }

    @Test
    fun attackTicksThatTriggerHealthWatchesFlushCombinedTargetDeltaBeforeRunningUpdate() = runTest {
        val fixture = createFixture(
            targetState = targetState(GameStateId("TARGET-IP")).copy(
                watches = WatchManagerState(
                    watches = listOf(healthWatch(threshold = 99.0)),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-health-running-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "logs", "watches", "stats"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun completedAttackCanStillFlushHealthWatchSideEffectsBeforeCompletedUpdate() = runTest {
        val fixture = createFixture(
            targetState = targetState(GameStateId("TARGET-IP"), health = 1.5).copy(
                watches = WatchManagerState(
                    watches = listOf(healthWatch()),
                ),
            ),
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-health-complete-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        val targetDelta = target.awaitFrame()
        val attackerDelta = attacker.awaitFrame()
        val updateFrame = attacker.awaitFrame()

        assertEquals(setOf("ports", "combat", "logs", "watches", "stats"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat", "ports", "runtime", "stats"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_COMPLETED, updateFrame.program_update?.status)
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun requestCancelAttackPublishesBilateralCleanupAndCancelledProgramUpdateBeforeResponse() = runTest {
        val fixture = createFixture()
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-cancel-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        attacker.awaitFrame()
        target.awaitFrame()
        attacker.awaitFrame()
        runCurrent()
        attacker.awaitFrame()

        attacker.send(
            RewriteFrames.command(
                commandId = "attack-cancel-1",
                commandName = "requestcancelattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestCancelAttackPayload.serializer(),
                    value = RequestCancelAttackPayload(
                        ip = "LOCAL-IP",
                        port = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val attackerDelta = attacker.awaitFrame()
        val targetDelta = target.awaitFrame()
        val updateFrame = attacker.awaitFrame()
        val responseFrame = attacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = AttackCancelResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("ports", "combat", "runtime"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(response.accepted)
        assertTrue(response.hadActiveSession)
    }

    @Test
    fun requestZombieAttackPublishesControllerZombieAndTargetFramesThenReturnsDedicatedResponse() = runTest {
        val zombieId = GameStateId("ZOMBIE-IP")
        val fixture = createFixture(
            extraStates = mapOf(
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle("LOCAL-IP"),
                ),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val zombie = fixture.authenticatedConnection("ZOMBIE-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-start-1",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "ZOMBIE-IP",
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                        windowHandle = 23,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val controllerDelta = controller.awaitFrame()
        val zombieDelta = zombie.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = controller.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = ZombieAttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy"), controllerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("ports", "combat", "runtime"), zombieDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
        assertEquals("LOCAL-IP", response.controllerStateId.value)
        assertEquals("ZOMBIE-IP", response.zombieStateId.value)
        assertEquals("TARGET-IP", response.targetStateId.value)
        assertEquals(25, response.targetPort)
        assertEquals(20.0, response.chargedAmount)
        assertEquals(80.0, response.controllerPettyCashAfter)
        assertEquals(8.0, response.zombieCpuLoadAfter)
        assertEquals("LOCAL-IP", response.session?.controllerStateId?.value)
        assertEquals("TARGET-IP", response.session?.targetStateId?.value)
        assertEquals(23, response.session?.windowHandle)

        runCurrent()

        val updateFrame = controller.awaitFrame()
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
        assertTrue(zombie.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun requestZombieAttackFallsBackToTheControllerHostWhenSourceIpIsMissing() = runTest {
        val fixture = createFixture(
            localState = attackerState(
                GameStateId("LOCAL-IP"),
                attackScriptBundle = zombieAuthorizedBundle("LOCAL-IP"),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-fallback-1",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = null,
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val controllerDelta = controller.awaitFrame()
        val targetDelta = target.awaitFrame()
        val responseFrame = controller.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = ZombieAttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("economy", "ports", "combat", "runtime"), controllerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertTrue(response.accepted)
        assertEquals("LOCAL-IP", response.zombieStateId.value)

        runCurrent()
        val updateFrame = controller.awaitFrame()
        assertEquals(ProgramStatus.PROGRAM_STATUS_RUNNING, updateFrame.program_update?.status)
    }

    @Test
    fun requestZombieAttackFailurePublishesPopupBeforeItsCorrelatedResponseWhenFundsAreInsufficient() = runTest {
        val zombieId = GameStateId("ZOMBIE-IP")
        val fixture = createFixture(
            localState = attackerState(GameStateId("LOCAL-IP")).copy(
                economy = attackerState(GameStateId("LOCAL-IP")).economy.copy(pettyCash = 10.0),
            ),
            extraStates = mapOf(
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle("LOCAL-IP"),
                ),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val zombie = fixture.authenticatedConnection("ZOMBIE-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-low-cash-1",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "ZOMBIE-IP",
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                        windowHandle = 23,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val popupFrame = controller.awaitFrame()
        val responseFrame = controller.awaitFrame()
        val popup = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = popupFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = ZombieAttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("popup", popupFrame.game_ui_event?.event_type)
        assertEquals(
            "It costs \$20 to attempt an attack from a zombie port.",
            assertIs<PopupUiEvent>(popup).message,
        )
        assertEquals(PopupUiStyle.ERROR, assertIs<PopupUiEvent>(popup).style)
        assertFalse(response.accepted)
        assertEquals(ZombieAttackStartFailureCode.INSUFFICIENT_PETTY_CASH, response.failureCode)
        assertTrue(zombie.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun requestZombieAttackAccessFailuresPublishGenericZombieUiEventsBeforeTheCorrelatedResponse() = runTest {
        val zombieId = GameStateId("ZOMBIE-IP")
        val fixture = createFixture(
            extraStates = mapOf(
                zombieId to attackerState(zombieId),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val zombie = fixture.authenticatedConnection("ZOMBIE-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-busy-1",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "ZOMBIE-IP",
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                        windowHandle = 23,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val eventFrame = controller.awaitFrame()
        val responseFrame = controller.awaitFrame()
        val event = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = eventFrame.game_ui_event!!.payload.toByteArray(),
        )
        val response = RewriteGameJson.decode(
            serializer = ZombieAttackStartResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("zombie_attack", eventFrame.game_ui_event?.event_type)
        val zombieEvent = assertIs<ZombieAttackUiEvent>(event)
        assertEquals("You cannot access this port.", zombieEvent.message)
        assertEquals("ZOMBIE-IP", zombieEvent.zombieIp)
        assertEquals(12, zombieEvent.sourcePort)
        assertFalse(response.accepted)
        assertEquals(ZombieAttackStartFailureCode.NOT_AUTHORIZED, response.failureCode)
        assertTrue(zombie.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun zombieContinueShowChoicesRoutesOnlyToControllerListeners() = runTest {
        val zombieId = GameStateId("ZOMBIE-IP")
        val fixture = createFixture(
            targetState = targetState(
                GameStateId("TARGET-IP"),
                installedApplication = InstalledApplication(
                    name = "target-http.bin",
                    kind = ApplicationKind.HTTP,
                    cpuCost = 2.0,
                ),
            ),
            extraStates = mapOf(
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle(
                        "LOCAL-IP",
                        continueScript = """int main() { showChoices(); cancelAttack(); return 0; }""",
                    ),
                ),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val zombie = fixture.authenticatedConnection("ZOMBIE-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-show-choices-start",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "ZOMBIE-IP",
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                        windowHandle = 23,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        controller.awaitFrame()
        zombie.awaitFrame()
        target.awaitFrame()
        controller.awaitFrame()
        runCurrent()
        controller.awaitFrame()

        advanceTimeBy(180_100)
        runCurrent()

        zombie.awaitFrame()
        target.awaitFrame()
        val controllerDelta = controller.awaitFrame()
        val showChoicesFrame = controller.awaitFrame()
        val showChoices = RewriteGameJson.decode(
            serializer = GameUiEvent.serializer(),
            payload = showChoicesFrame.game_ui_event!!.payload.toByteArray(),
        )
        val updateFrame = controller.awaitFrame()

        assertEquals(setOf("stats"), controllerDelta.delta?.delta_keys?.toSet())
        assertEquals("show_choices", showChoicesFrame.game_ui_event?.event_type)
        val event = assertIs<ShowChoicesUiEvent>(showChoices)
        assertEquals("TARGET-IP", event.targetIp)
        assertEquals(25, event.targetPort)
        assertEquals(ShowChoicesType.HTTP, event.choiceType)
        assertEquals(23, event.windowHandle)
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(zombie.drainFrames().none { it.game_ui_event != null || it.program_update != null })
        assertTrue(target.drainFrames().none { it.game_ui_event != null || it.program_update != null })
    }

    @Test
    fun requestZombieCancelAttackPublishesCleanupAndCancelledUpdateBeforeResponse() = runTest {
        val zombieId = GameStateId("ZOMBIE-IP")
        val fixture = createFixture(
            extraStates = mapOf(
                zombieId to attackerState(
                    zombieId,
                    attackScriptBundle = zombieAuthorizedBundle("LOCAL-IP"),
                ),
            ),
        )
        val controller = fixture.authenticatedConnection("LOCAL-IP")
        val zombie = fixture.authenticatedConnection("ZOMBIE-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-cancel-start",
                commandName = "requestzombieattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieAttackPayload.serializer(),
                    value = RequestZombieAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "ZOMBIE-IP",
                        sourcePort = 12,
                        parentIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )
        controller.awaitFrame()
        zombie.awaitFrame()
        target.awaitFrame()
        controller.awaitFrame()
        runCurrent()
        controller.awaitFrame()

        controller.send(
            RewriteFrames.command(
                commandId = "zombie-attack-cancel-1",
                commandName = "requestzombiecancelattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestZombieCancelAttackPayload.serializer(),
                    value = RequestZombieCancelAttackPayload(
                        ip = "ZOMBIE-IP",
                        port = 12,
                        targetIp = "LOCAL-IP",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val zombieDelta = zombie.awaitFrame()
        val targetDelta = target.awaitFrame()
        val updateFrame = controller.awaitFrame()
        val responseFrame = controller.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = ZombieAttackCancelResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("ports", "combat", "runtime"), zombieDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(response.accepted)
        assertTrue(response.hadActiveSession)
        assertTrue(zombie.drainFrames().isEmpty())
        assertTrue(target.drainFrames().isEmpty())
    }

    @Test
    fun cancelCleanupDoesNotRunFinalizeScripts() = runTest {
        val finalizeBundle = attackScriptBundle(
            finalize = """int main() { logMessage("should-not-run"); return 0; }""",
        )
        val cancelFixture = createFixture(
            localState = attackerState(GameStateId("LOCAL-IP"), attackScriptBundle = finalizeBundle),
        )
        val cancelAttacker = cancelFixture.authenticatedConnection("LOCAL-IP")
        val cancelTarget = cancelFixture.authenticatedConnection("TARGET-IP")

        cancelAttacker.send(
            RewriteFrames.command(
                commandId = "attack-cancel-finalize-start",
                commandName = "requestattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestAttackPayload.serializer(),
                    value = RequestAttackPayload(
                        targetIp = "TARGET-IP",
                        targetPort = 25,
                        sourceIp = "LOCAL-IP",
                        sourcePort = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        cancelAttacker.awaitFrame()
        cancelTarget.awaitFrame()
        cancelAttacker.awaitFrame()
        runCurrent()
        cancelAttacker.awaitFrame()

        cancelAttacker.send(
            RewriteFrames.command(
                commandId = "attack-cancel-finalize",
                commandName = "requestcancelattack",
                payload = RewriteGameJson.encode(
                    serializer = RequestCancelAttackPayload.serializer(),
                    value = RequestCancelAttackPayload(
                        ip = "LOCAL-IP",
                        port = 12,
                    ),
                ),
                expectsResponse = true,
            ),
        )
        val attackerDelta = cancelAttacker.awaitFrame()
        val targetDelta = cancelTarget.awaitFrame()
        val updateFrame = cancelAttacker.awaitFrame()
        val responseFrame = cancelAttacker.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = AttackCancelResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(setOf("ports", "combat", "runtime"), attackerDelta.delta?.delta_keys?.toSet())
        assertEquals(setOf("combat"), targetDelta.delta?.delta_keys?.toSet())
        assertEquals(ProgramStatus.PROGRAM_STATUS_CANCELLED, updateFrame.program_update?.status)
        assertTrue(response.accepted)
    }

    @Test
    fun bootstrapReturnsOneSnapshotAndClearsStaleBilateralAttackRuntimeWithoutPreBootstrapDeltas() = runTest {
        val staleState = attackerState(GameStateId("LOCAL-IP")).copy(
            ports = attackerState(GameStateId("LOCAL-IP")).ports.map { port ->
                if (port.number == 12) port.copy(attacking = true) else port
            },
            combat = CombatState(
                activeAttacksBySourcePort = mapOf(
                    12 to AttackSessionState(
                        programId = "stale-program",
                        sourcePort = 12,
                        targetStateId = GameStateId("TARGET-IP"),
                        targetPort = 25,
                        iterationCount = 3,
                    ),
                ),
            ),
            runtime = RuntimeState(currentCpuLoad = 8.0),
        )
        val staleTargetState = targetState(GameStateId("TARGET-IP")).copy(
            combat = CombatState(
                incomingAttacksByTargetPort = mapOf(
                    25 to com.hackwars.rewrite.gamecore.IncomingAttackState(
                        attackerStateId = GameStateId("LOCAL-IP"),
                        attackerSourcePort = 12,
                        targetPort = 25,
                        startedAtEpochMillis = 1_000L,
                        windowHandle = 0,
                    ),
                ),
            ),
        )
        val fixture = createFixture(localState = staleState, targetState = staleTargetState)
        val attacker = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        assertTrue(attacker.bootstrap.combat.activeAttacksBySourcePort.isEmpty())
        assertFalse(attacker.bootstrap.ports.first { it.number == 12 }.attacking)
        assertEquals(0.0, attacker.bootstrap.runtime.currentCpuLoad)
        assertTrue(target.bootstrap.combat.incomingAttacksByTargetPort.isEmpty())
        assertTrue(attacker.connection.drainFrames().isEmpty())
        assertTrue(target.connection.drainFrames().isEmpty())
    }

    @Test
    fun sessionBootstrapSchedulesCombatMaintenanceRuntimeAndSessionEndCancelsIt() = runTest {
        val localId = GameStateId("LOCAL-IP")
        val damagedState = attackerState(localId).copy(
            ports = attackerState(localId).ports + PortState(
                number = 22,
                type = "http",
                enabled = true,
                health = 80.0,
                installedApplication = InstalledApplication(
                    name = "target-http.bin",
                    kind = ApplicationKind.HTTP,
                    cpuCost = 2.0,
                ),
            ),
            watches = WatchManagerState(
                watches = listOf(
                    InstalledWatch(
                        kind = WatchKind.HEALTH,
                        enabled = true,
                        note = "health-watch",
                        cpuCost = 5.0,
                        quantityThreshold = 50.0,
                        baselineQuantity = 80.0,
                        installPort = 22,
                        searchFirewallType = 0,
                        observedPorts = listOf(22),
                        contents = "watch",
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.WATCH,
                            applicationKind = ApplicationKind.WATCH,
                            outputName = "watch.bin",
                        ),
                    ),
                ),
            ),
        )
        val fixture = createFixture(
            localState = damagedState,
            enableCombatMaintenanceRuntime = true,
        )
        val attacker = fixture.authenticatedConnection("LOCAL-IP", drainPostBootstrapFrames = false)

        runCurrent()

        val maintenanceFrames = attacker.connection.drainFrames()
        val maintenanceDelta = maintenanceFrames.first { it.delta != null }
        val updatedState = requireNotNull(fixture.repository.load(localId))

        assertEquals(setOf("ports", "watches", "runtime"), maintenanceDelta.delta?.delta_keys?.toSet())
        assertTrue(maintenanceFrames.none { it.program_update != null })
        assertEquals(81.0, updatedState.ports.first { it.number == 22 }.health)
        assertEquals(81.0, updatedState.watches.watches.single().baselineQuantity)
        assertEquals(1L, updatedState.runtime.healCounter)

        attacker.connection.close()
        runCurrent()
        advanceTimeBy(2_100L)
        runCurrent()

        val afterClose = requireNotNull(fixture.repository.load(localId))
        assertEquals(1L, afterClose.runtime.healCounter)
        assertTrue(attacker.connection.drainFrames().isEmpty())
    }

    private fun TestScope.createFixture(
        localState: ComputerState = attackerState(GameStateId("LOCAL-IP")),
        targetState: ComputerState = targetState(GameStateId("TARGET-IP")),
        extraStates: Map<GameStateId, ComputerState> = emptyMap(),
        enableCombatMaintenanceRuntime: Boolean = false,
    ): Fixture {
        val seededStates = linkedMapOf(
            GameStateId("LOCAL-IP") to localState,
            GameStateId("TARGET-IP") to targetState,
        ).apply {
            putAll(extraStates)
        }
        val repository = InMemoryComputerStateRepository(
            seededStates = seededStates,
        )
        val interests = InMemoryInterestRegistry()
        val registry = InMemoryAttackProgramRegistry()
        val accounts = seededStates.values.map { state ->
            FakePlayerAccount(
                playFabId = state.identity.playFabId.ifBlank { "PF-${state.id.value}" },
                playerIp = state.id.value,
                sessionTicket = "SESSION-${state.id.value}",
            )
        }
        val combatMaintenanceRegistry: CombatMaintenanceProgramRegistry =
            if (enableCombatMaintenanceRuntime) {
                com.hackwars.rewrite.gamecore.InMemoryCombatMaintenanceProgramRegistry()
            } else {
                DisabledCombatMaintenanceProgramRegistry
            }
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
                attackProgramRegistry = registry,
                programScheduler = CoroutineProgramScheduler(
                    dispatcher = null,
                    interestRegistry = interests,
                    coroutineScope = backgroundScope,
                ),
            ),
            ftpPasswordRepository = InMemoryFtpPasswordRepository(),
            interestRegistry = interests,
            serverId = "1",
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
            attackProgramRegistry = registry,
            combatMaintenanceProgramRegistry = combatMaintenanceRegistry,
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = accounts,
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 600.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)
        return Fixture(
            harness = harness,
            repository = repository,
            sessionTicketsByIp = accounts.associate { it.playerIp to it.sessionTicket },
            playFabIdsByIp = accounts.associate { it.playerIp to it.playFabId },
        )
    }

    private suspend fun Fixture.authenticatedConnection(
        requestedIp: String,
        drainPostBootstrapFrames: Boolean = true,
    ): AuthenticatedConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = sessionTicketsByIp[requestedIp] ?: error("No session ticket for $requestedIp"),
                clientBuild = "rewrite-attack-it",
                playFabIdHint = playFabIdsByIp[requestedIp] ?: error("No PlayFab id for $requestedIp"),
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        val snapshot = connection.awaitFrame()
        yield()
        if (drainPostBootstrapFrames) {
            connection.drainFrames()
        }
        return AuthenticatedConnection(
            connection = connection,
            bootstrap = RewriteGameJson.decode(
                serializer = ComputerState.serializer(),
                payload = snapshot.snapshot!!.payload.toByteArray(),
            ),
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val repository: InMemoryComputerStateRepository,
        val sessionTicketsByIp: Map<String, String>,
        val playFabIdsByIp: Map<String, String>,
    )

    private data class AuthenticatedConnection(
        val connection: InMemoryClientConnection,
        val bootstrap: ComputerState,
    ) {
        suspend fun send(frame: FrameEnvelope) = connection.send(frame)
        suspend fun awaitFrame(): FrameEnvelope = connection.awaitFrame()
        fun drainFrames(): List<FrameEnvelope> = connection.drainFrames()
    }

    private object DisabledCombatMaintenanceProgramRegistry : CombatMaintenanceProgramRegistry {
        override suspend fun register(
            stateId: GameStateId,
            programId: String,
            handle: com.hackwars.rewrite.gamecore.ProgramHandle,
        ) = Unit

        override suspend fun programIdFor(stateId: GameStateId): String? = null

        override suspend fun hasProgram(stateId: GameStateId): Boolean = true

        override suspend fun cancel(stateId: GameStateId, reason: String): Boolean = false

        override suspend fun unregister(programId: String) = Unit
    }

    private class HarnessBackedGameAdapter(
        private val adapter: RewriteGameProtocolAdapter,
    ) : RewriteServiceAdapter {
        override val service = RewriteService.GAME

        private lateinit var harness: InMemoryRewriteServiceHarness

        fun attachHarness(harness: InMemoryRewriteServiceHarness) {
            this.harness = harness
        }

        override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
            return adapter.onSessionStarted(
                session = session.toGameSession(),
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: hackwars.rewrite.v1.CommandEnvelope,
        ): List<FrameEnvelope> {
            return adapter.onCommand(
                session = session.toGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }

        override suspend fun onSessionEnded(session: InMemoryAuthenticatedSession) {
            adapter.onSessionEnded(session.toGameSession())
        }
    }

    private fun attackerState(
        stateId: GameStateId,
        attackScriptBundle: ProgramScriptBundle? = null,
        filesystemFiles: List<StoredFile> = emptyList(),
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            economy = EconomyState(
                pettyCash = 100.0,
                bankMoney = 50.0,
                defaultBankPort = 6,
            ),
            hardware = HardwareState(cpuMax = 100.0, hdMaximum = 100),
            ports = listOf(
                PortState(
                    number = 6,
                    type = "bank",
                    enabled = true,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                        cpuCost = 2.0,
                    ),
                ),
                PortState(
                    number = 12,
                    type = "attack",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "attack.bin",
                        kind = ApplicationKind.ATTACK,
                        cpuCost = 8.0,
                        scriptBundle = attackScriptBundle,
                    ),
                ),
            ),
            filesystem = filesystemFiles.fold(FilesystemState()) { filesystem, file ->
                filesystem.saveFile(file)
            },
        )
    }

    private fun targetState(
        stateId: GameStateId,
        health: Double = 100.0,
        pettyCash: Double = 0.0,
        logs: List<ComputerLogEntry> = emptyList(),
        firewallCombatProfile: FirewallCombatProfile = FirewallCombatProfile(),
        firewallActionProfile: FirewallActionProfile = FirewallActionProfile(),
        portType: String = "http",
        installedApplication: InstalledApplication? = null,
        additionalPorts: List<PortState> = emptyList(),
        filesystemFiles: List<StoredFile> = emptyList(),
        isNpc: Boolean = false,
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}", isNpc = isNpc).copy(
            economy = EconomyState(
                pettyCash = pettyCash,
            ),
            logs = LogState(logs),
            filesystem = filesystemFiles.fold(FilesystemState()) { filesystem, file ->
                filesystem.saveFile(file)
            },
            ports = buildList {
                add(
                    PortState(
                        number = 25,
                        type = portType,
                        enabled = true,
                        health = health,
                        installedApplication = installedApplication,
                        installedFirewall = InstalledFirewall(
                            name = "target-wall.bin",
                            combatProfile = firewallCombatProfile,
                            actionProfile = firewallActionProfile,
                        ),
                    ),
                )
                addAll(additionalPorts)
            },
        )
    }

    private fun healthWatch(threshold: Double = 50.0): InstalledWatch {
        return InstalledWatch(
            kind = WatchKind.HEALTH,
            enabled = true,
            note = "health-watch",
            cpuCost = 5.0,
            quantityThreshold = threshold,
            baselineQuantity = 100.0,
            installPort = 25,
            searchFirewallType = 0,
            observedPorts = listOf(25),
            contents = """int main() { logMessage("health"); return 0; }""",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
        )
    }

    private fun storedFile(
        path: String,
        name: String,
        quantity: Int = 1,
        kind: com.hackwars.rewrite.gamecore.StoredFileKind = com.hackwars.rewrite.gamecore.StoredFileKind.TEXT,
        contents: String = name,
        compiledBinary: CompiledBinaryMetadata? = null,
        scriptBundle: ProgramScriptBundle? = null,
    ): StoredFile {
        return StoredFile(
            path = buildFilePath(path, name),
            name = name,
            kind = kind,
            contents = contents,
            quantity = quantity,
            compiledBinary = compiledBinary,
            scriptBundle = scriptBundle,
        )
    }

    private fun targetWatch(
        note: String,
        kind: WatchKind,
        installPort: Int,
        cpuCost: Double,
        enabled: Boolean = true,
    ): InstalledWatch {
        return InstalledWatch(
            kind = kind,
            enabled = enabled,
            note = note,
            cpuCost = cpuCost,
            quantityThreshold = 50.0,
            baselineQuantity = 100.0,
            installPort = installPort,
            searchFirewallType = 0,
            observedPorts = listOf(installPort),
            contents = "watch",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.WATCH,
                applicationKind = ApplicationKind.WATCH,
                outputName = "watch.bin",
            ),
        )
    }

    private fun attackScriptBundle(
        initialize: String? = null,
        continueScript: String? = null,
        finalize: String? = null,
    ): ProgramScriptBundle {
        return ProgramScriptBundle(
            family = ScriptFamily.ATTACK,
            scriptsBySlot = buildMap {
                initialize?.let { put(ProgramScriptSlot.INITIALIZE, it) }
                continueScript?.let { put(ProgramScriptSlot.CONTINUE, it) }
                finalize?.let { put(ProgramScriptSlot.FINALIZE, it) }
            },
        )
    }

    private fun redirectScriptBundle(
        initialize: String? = null,
        continueScript: String? = null,
        finalize: String? = null,
    ): ProgramScriptBundle {
        return ProgramScriptBundle(
            family = ScriptFamily.REDIRECT,
            scriptsBySlot = buildMap {
                initialize?.let { put(ProgramScriptSlot.INITIALIZE, it) }
                continueScript?.let { put(ProgramScriptSlot.CONTINUE, it) }
                finalize?.let { put(ProgramScriptSlot.FINALIZE, it) }
            },
        )
    }

    private fun redirectAttackerState(
        stateId: GameStateId,
        redirectScriptBundle: ProgramScriptBundle? = null,
        redirectXp: Double = 0.0,
    ): ComputerState {
        val base = attackerState(stateId)
        return base.copy(
            stats = base.stats.copy(
                experienceByFamily = base.stats.experienceByFamily + (ScriptFamily.REDIRECT to redirectXp),
            ),
            ports = base.ports.map { port ->
                if (port.number == 12) {
                    port.copy(
                        type = "redirect",
                        installedApplication = InstalledApplication(
                            name = "redirect.bin",
                            kind = ApplicationKind.REDIRECT,
                            cpuCost = 8.0,
                            scriptBundle = redirectScriptBundle,
                        ),
                    )
                } else {
                    port
                }
            },
        )
    }

    private fun redirectTargetState(
        stateId: GameStateId,
        health: Double = 100.0,
        isNpc: Boolean = false,
        commodities: List<Double> = List(5) { 0.0 },
        commodityRespawn: List<Double> = commodities,
    ): ComputerState {
        val base = targetState(
            stateId = stateId,
            health = health,
            isNpc = isNpc,
            portType = "redirect",
            installedApplication = InstalledApplication(
                name = "redirect-target.bin",
                kind = ApplicationKind.REDIRECT,
                cpuCost = 4.0,
            ),
        )
        return base.copy(
            economy = base.economy.copy(
                commodities = List(5) { index -> commodities.getOrNull(index) ?: 0.0 },
                commodityRespawn = List(5) { index -> commodityRespawn.getOrNull(index) ?: 0.0 },
            ),
        )
    }

    private fun zombieAuthorizedBundle(
        controllerIp: String,
        continueScript: String? = null,
        finalize: String? = null,
    ): ProgramScriptBundle {
        return attackScriptBundle(
            initialize = """int main() { zombie("$controllerIp"); return 0; }""",
            continueScript = continueScript,
            finalize = finalize,
        )
    }
}

private fun InMemoryAuthenticatedSession.toGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
