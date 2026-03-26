package com.hackwars.rewrite.client.web

import com.hackwars.rewrite.client.RewriteGameCommandResult
import com.hackwars.rewrite.client.RewriteGameConnectionConfig
import com.hackwars.rewrite.client.RewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteLoginAuthResult
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientPurchaseResponse
import com.hackwars.rewrite.protocol.ClientRequestPurchasePayload
import com.hackwars.rewrite.protocol.ClientRequestWebpagePayload
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientSubmitWebpagePayload
import com.hackwars.rewrite.protocol.ClientVotePayload
import com.hackwars.rewrite.protocol.ClientVoteResponse
import com.hackwars.rewrite.protocol.ClientWebsiteRenderResponse
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteWebBrowserTest {
    @Test
    fun controllerWebHelpersSendExpectedPayloadsAndExitUsesFireAndForget() = runTest {
        val sessionGateway = FakeWebSessionGateway()
        val controller = testController(sessionGateway, testScheduler)
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

        val webpagePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestWebpage(
                targetIp = "198.51.100.40",
                parameters = mapOf("category" to "software"),
            )
        }
        runCurrent()
        val session = sessionGateway.requireLatestGameSession()
        val webpageCommand = session.sentFrames.last().command!!
        val webpagePayload = RewriteClientJson.decode(
            ClientRequestWebpagePayload.serializer(),
            webpageCommand.payload.toByteArray(),
        )
        assertEquals("requestwebpage", webpageCommand.command_name)
        assertEquals("198.51.100.40", webpagePayload.targetIp)
        assertEquals("192.0.2.10", webpagePayload.sourceIp)
        assertEquals("software", webpagePayload.parameters["category"])
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = webpageCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientWebsiteRenderResponse.serializer(),
                    ClientWebsiteRenderResponse(
                        resolvedTargetStateId = "198.51.100.40",
                        title = "Store",
                        body = "<html><body>Store</body></html>",
                        version = 2,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientWebsiteRenderResponse>>(webpagePending.await())

        val submitPending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.submitWebpage(
                targetIp = "198.51.100.40",
                parameters = mapOf("buy" to "attack.bin"),
            )
        }
        runCurrent()
        val submitCommand = session.sentFrames.last().command!!
        val submitPayload = RewriteClientJson.decode(
            ClientSubmitWebpagePayload.serializer(),
            submitCommand.payload.toByteArray(),
        )
        assertEquals("submit", submitCommand.command_name)
        assertEquals("198.51.100.40", submitPayload.targetIp)
        assertEquals("attack.bin", submitPayload.parameters["buy"])
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = submitCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientWebsiteRenderResponse.serializer(),
                    ClientWebsiteRenderResponse(
                        resolvedTargetStateId = "198.51.100.40",
                        title = "Submitted",
                        body = "<html><body>Submitted</body></html>",
                        version = 3,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientWebsiteRenderResponse>>(submitPending.await())

        val votePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.voteForWebsite("198.51.100.40")
        }
        runCurrent()
        val voteCommand = session.sentFrames.last().command!!
        val votePayload = RewriteClientJson.decode(
            ClientVotePayload.serializer(),
            voteCommand.payload.toByteArray(),
        )
        assertEquals("vote", voteCommand.command_name)
        assertEquals("198.51.100.40", votePayload.targetIp)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = voteCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientVoteResponse.serializer(),
                    ClientVoteResponse(
                        voterStateId = "192.0.2.10",
                        targetStateId = "198.51.100.40",
                        votesAvailableAfter = 1,
                        targetVoteCountAfter = 4,
                        targetHttpExperienceAfter = 12.0,
                        voterVersion = 4,
                        targetVersion = 5,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientVoteResponse>>(votePending.await())

        val purchasePending = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            controller.requestPurchase(
                targetIp = "198.51.100.40",
                fileName = "attack.bin",
                quantity = 2,
            )
        }
        runCurrent()
        val purchaseCommand = session.sentFrames.last().command!!
        val purchasePayload = RewriteClientJson.decode(
            ClientRequestPurchasePayload.serializer(),
            purchaseCommand.payload.toByteArray(),
        )
        assertEquals("requestpurchase", purchaseCommand.command_name)
        assertEquals("198.51.100.40", purchasePayload.targetIp)
        assertEquals("attack.bin", purchasePayload.fileName)
        assertEquals(2, purchasePayload.quantity)
        controller.accept(
            RewriteService.GAME,
            RewriteFrames.commandResponse(
                commandId = purchaseCommand.command_id,
                payload = RewriteClientJson.encode(
                    ClientPurchaseResponse.serializer(),
                    ClientPurchaseResponse(
                        buyerStateId = "192.0.2.10",
                        sellerStateId = "198.51.100.40",
                        revenueTargetStateId = "198.51.100.40",
                        purchasedFile = ClientStoredFile(
                            path = "/Store/attack.bin",
                            name = "attack.bin",
                            kind = ClientStoredFileKind.APPLICATION_BINARY,
                        ),
                        fulfilledQuantity = 2,
                        totalPrice = 50.0,
                        buyerVersion = 6,
                        sellerVersion = 7,
                        revenueTargetVersion = 8,
                    ),
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<RewriteGameCommandResult.Success<ClientPurchaseResponse>>(purchasePending.await())

        controller.exitWebpage("198.51.100.40")
        runCurrent()
        advanceUntilIdle()

        val exitCommand = session.sentFrames.last().command!!
        assertEquals("exit", exitCommand.command_name)
        assertFalse(exitCommand.expects_response)
    }

    @Test
    fun browserNormalizationHistoryAndNavigationParsingFollowLockedRules() {
        assertEquals("example.com", normalizeBrowserTarget(" https://Example.COM/shop/?a=1 "))
        assertEquals("198.51.100.40", normalizeBrowserTarget("198.51.100.40/"))

        val absolute = parseBrowserAddressNavigation("Example.com/store?buy=attack.bin&quantity=2")
        assertNotNull(absolute)
        assertEquals("example.com", absolute.target)
        assertEquals("attack.bin", absolute.parameters["buy"])
        assertEquals("2", absolute.parameters["quantity"])

        val relative = parseBrowserHyperlinkNavigation("/shop?buy=watch.bin", "198.51.100.40")
        assertNotNull(relative)
        assertEquals("198.51.100.40", relative.target)
        assertEquals("watch.bin", relative.parameters["buy"])

        val submitted = parseBrowserFormNavigation(
            actionReference = "https://Store.HackWars.Net/checkout?view=cart",
            currentResolvedTarget = "198.51.100.40",
            parameters = mapOf("quantity" to "3"),
        )
        assertNotNull(submitted)
        assertEquals("store.hackwars.net", submitted.target)
        assertEquals("cart", submitted.parameters["view"])
        assertEquals("3", submitted.parameters["quantity"])
        assertEquals(RewriteWebNavigationMode.SUBMIT, submitted.mode)

        val history = RewriteWebHistory(maxEntries = 10)
        repeat(12) { index ->
            history.push(RewriteWebNavigationIntent(target = "site-$index"))
        }
        val visitedTargets = mutableListOf(history.current()!!.target)
        while (history.canGoBack()) {
            visitedTargets += history.back()!!.target
        }
        assertEquals(listOf("site-11", "site-10", "site-9", "site-8", "site-7", "site-6", "site-5", "site-4", "site-3", "site-2"), visitedTargets)
    }

    private fun testController(
        sessionGateway: FakeWebSessionGateway,
        scheduler: TestCoroutineScheduler,
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(),
            authGateway = FakeWebAuthGateway(),
            sessionGateway = sessionGateway,
            workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
        )
    }

    private class FakeWebAuthGateway : RewriteLoginAuthGateway {
        override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
            password.fill('\u0000')
            return RewriteLoginAuthResult.success("PF-LOCAL", "SESSION-LOCAL")
        }
    }

    private class FakeWebSessionGateway : RewriteServiceSessionGateway {
        private val sessions = mutableListOf<FakeWebSession>()

        override fun open(
            service: RewriteService,
            onInboundFrame: (FrameEnvelope) -> Unit,
        ): RewriteServiceSession {
            return FakeWebSession(service, onInboundFrame).also { sessions += it }
        }

        fun requireLatestGameSession(): FakeWebSession {
            return sessions.last { it.service == RewriteService.GAME }
        }
    }

    private class FakeWebSession(
        override val service: RewriteService,
        private val onInboundFrame: (FrameEnvelope) -> Unit,
    ) : RewriteServiceSession {
        val sentFrames = mutableListOf<FrameEnvelope>()
        var closed: Boolean = false

        override suspend fun send(frame: FrameEnvelope) {
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
