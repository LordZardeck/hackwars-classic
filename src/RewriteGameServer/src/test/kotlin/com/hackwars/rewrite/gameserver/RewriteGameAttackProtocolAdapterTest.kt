package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.AttackCancelResponse
import com.hackwars.rewrite.gamecore.AttackMessageUiEvent
import com.hackwars.rewrite.gamecore.AttackSessionState
import com.hackwars.rewrite.gamecore.AttackStartResponse
import com.hackwars.rewrite.gamecore.ChangeDailyPayPayload
import com.hackwars.rewrite.gamecore.ChangeDailyPayOutcome
import com.hackwars.rewrite.gamecore.ChangeDailyPayResponse
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.CombatState
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.ComputerLogEntry
import com.hackwars.rewrite.gamecore.CoroutineProgramScheduler
import com.hackwars.rewrite.gamecore.DailyPayState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.FilesystemState
import com.hackwars.rewrite.gamecore.FirewallCombatProfile
import com.hackwars.rewrite.gamecore.FirewallActionProfile
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.GameUiEvent
import com.hackwars.rewrite.gamecore.HardwareState
import com.hackwars.rewrite.gamecore.InMemoryAttackProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
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
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.RuntimeState
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.TextMessageUiEvent
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WebsiteState
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

        assertEquals(setOf("economy", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
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

        assertEquals(setOf("filesystem", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
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
        assertEquals(0.0, updatedTarget.ports.first { it.number == 25 }.health)
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

        assertEquals(setOf("logs", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
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

        assertEquals(setOf("watches", "runtime", "ports", "combat"), targetDelta.delta?.delta_keys?.toSet())
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

    private fun TestScope.createFixture(
        localState: ComputerState = attackerState(GameStateId("LOCAL-IP")),
        targetState: ComputerState = targetState(GameStateId("TARGET-IP")),
    ): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState,
                GameStateId("TARGET-IP") to targetState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val registry = InMemoryAttackProgramRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
                programScheduler = CoroutineProgramScheduler(
                    dispatcher = null,
                    interestRegistry = interests,
                    coroutineScope = backgroundScope,
                ),
            ),
            interestRegistry = interests,
            serverId = "1",
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
            attackProgramRegistry = registry,
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount("PF-LOCALUSER", "LOCAL-IP", "SESSION-LOCALUSER"),
                        FakePlayerAccount("PF-TARGET", "TARGET-IP", "SESSION-TARGET"),
                    ),
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
        )
    }

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): AuthenticatedConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = sessionTicketFor(requestedIp),
                clientBuild = "rewrite-attack-it",
                playFabIdHint = playFabIdFor(requestedIp),
                requestedIp = requestedIp,
            ),
        )
        connection.awaitFrame()
        val snapshot = connection.awaitFrame()
        return AuthenticatedConnection(
            connection = connection,
            bootstrap = RewriteGameJson.decode(
                serializer = ComputerState.serializer(),
                payload = snapshot.snapshot!!.payload.toByteArray(),
            ),
        )
    }

    private fun sessionTicketFor(requestedIp: String): String = when (requestedIp) {
        "LOCAL-IP" -> "SESSION-LOCALUSER"
        "TARGET-IP" -> "SESSION-TARGET"
        else -> error("No session ticket for $requestedIp")
    }

    private fun playFabIdFor(requestedIp: String): String = when (requestedIp) {
        "LOCAL-IP" -> "PF-LOCALUSER"
        "TARGET-IP" -> "PF-TARGET"
        else -> error("No PlayFab id for $requestedIp")
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
        val repository: InMemoryComputerStateRepository,
    )

    private data class AuthenticatedConnection(
        val connection: InMemoryClientConnection,
        val bootstrap: ComputerState,
    ) {
        suspend fun send(frame: FrameEnvelope) = connection.send(frame)
        suspend fun awaitFrame(): FrameEnvelope = connection.awaitFrame()
        fun drainFrames(): List<FrameEnvelope> = connection.drainFrames()
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
}

private fun InMemoryAuthenticatedSession.toGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}
