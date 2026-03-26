package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.network.buildZombieAttackExtraInfo
import com.hackwars.rewrite.protocol.ClientAttackMode
import com.hackwars.rewrite.protocol.ClientAttackSessionKind
import com.hackwars.rewrite.protocol.ClientAttackSessionState
import com.hackwars.rewrite.protocol.ClientFloatHookValue
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientRequestZombieAttackPayload
import com.hackwars.rewrite.protocol.ClientRequestZombieCancelAttackPayload
import com.hackwars.rewrite.protocol.ClientZombieAttackCancelResponse
import com.hackwars.rewrite.protocol.ClientZombieAttackStartResponse
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteZombieAttackWindowsTest {
    @Test
    fun zombieExtraInfoPreservesLockedLegacyLayout() {
        val extraInfo = buildZombieAttackExtraInfo(pettyCashTarget = 75.0)

        assertEquals(5, extraInfo.size)
        assertEquals("", (extraInfo[0] as com.hackwars.rewrite.protocol.ClientStringHookValue).value)
        assertEquals(0.0, (extraInfo[1] as ClientFloatHookValue).value)
        assertEquals("", (extraInfo[2] as com.hackwars.rewrite.protocol.ClientStringHookValue).value)
        assertEquals(75.0, (extraInfo[3] as ClientFloatHookValue).value)
        assertEquals("", (extraInfo[4] as com.hackwars.rewrite.protocol.ClientStringHookValue).value)
    }

    @Test
    fun controllerHelpersSendExpectedZombieAttackPayloads() = runTest {
        val sessionGateway = FakeZombieAttackSessionGateway()
        val controller = testController(sessionGateway = sessionGateway, scheduler = testScheduler)
        acceptGameAuth(controller)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.snapshot(
                gameStateId = "LOCAL-IP",
                sequence = 1,
                payload = RewriteClientJson.encode(
                    ClientGameSnapshot.serializer(),
                    ClientGameSnapshot(id = "LOCAL-IP"),
                ),
            ),
        )

        val attackPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestZombieAttack(
                targetIp = "TARGET-IP",
                targetPort = 4,
                zombieIp = "ZOMBIE-IP",
                zombiePort = 12,
                secondaryPorts = listOf(9, 10),
                extraInfo = buildZombieAttackExtraInfo(50.0),
            )
        }
        runCurrent()

        val session = sessionGateway.requireLatestGameSession()
        val attackCommand = session.sentFrames.last().command!!
        val attackPayload = RewriteClientJson.decode(
            ClientRequestZombieAttackPayload.serializer(),
            attackCommand.payload.toByteArray(),
        )

        assertEquals("requestzombieattack", attackCommand.command_name)
        assertEquals("TARGET-IP", attackPayload.targetIp)
        assertEquals("ZOMBIE-IP", attackPayload.sourceIp)
        assertEquals(12, attackPayload.sourcePort)
        assertEquals(listOf(9, 10), attackPayload.secondaryPorts)
        assertNull(attackPayload.scripts)
        assertEquals("LOCAL-IP", attackPayload.parentIp)
        assertEquals(50.0, (attackPayload.extraInfo!![3] as ClientFloatHookValue).value)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = attackCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientZombieAttackStartResponse.serializer(),
                    ClientZombieAttackStartResponse(
                        controllerStateId = "LOCAL-IP",
                        zombieStateId = "ZOMBIE-IP",
                        sourcePort = 12,
                        targetStateId = "TARGET-IP",
                        targetPort = 4,
                        accepted = true,
                        message = "zombie-attack-started",
                        chargedAmount = 20.0,
                        controllerPettyCashAfter = 80.0,
                        zombieCpuLoadAfter = 6.0,
                        session = ClientAttackSessionState(
                            programId = "zombie-program-1",
                            sourcePort = 12,
                            targetStateId = "TARGET-IP",
                            targetPort = 4,
                            sessionKind = ClientAttackSessionKind.ATTACK,
                            attackMode = ClientAttackMode.ZOMBIE,
                            windowHandle = 0,
                            secondaryPorts = listOf(9, 10),
                        ),
                        controllerVersion = 7,
                        zombieVersion = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientZombieAttackStartResponse>>(attackPending.await())

        val cancelPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestZombieCancelAttack(
                zombieIp = "ZOMBIE-IP",
                zombiePort = 12,
            )
        }
        runCurrent()

        val cancelCommand = session.sentFrames.last().command!!
        val cancelPayload = RewriteClientJson.decode(
            ClientRequestZombieCancelAttackPayload.serializer(),
            cancelCommand.payload.toByteArray(),
        )

        assertEquals("requestzombiecancelattack", cancelCommand.command_name)
        assertEquals("LOCAL-IP", cancelPayload.ip)
        assertEquals(12, cancelPayload.port)
        assertEquals("ZOMBIE-IP", cancelPayload.targetIp)

        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = cancelCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientZombieAttackCancelResponse.serializer(),
                    ClientZombieAttackCancelResponse(
                        controllerStateId = "LOCAL-IP",
                        zombieStateId = "ZOMBIE-IP",
                        sourcePort = 12,
                        accepted = true,
                        hadActiveSession = true,
                        message = "zombie-attack-cancelled",
                        controllerVersion = 8,
                        zombieVersion = 4,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientZombieAttackCancelResponse>>(cancelPending.await())
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

    private class FakeZombieAttackSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeZombieAttackSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeZombieAttackSession(service, onInboundFrame).also(sessions::add)
        }

        fun requireLatestGameSession(): FakeZombieAttackSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeZombieAttackSession(
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
