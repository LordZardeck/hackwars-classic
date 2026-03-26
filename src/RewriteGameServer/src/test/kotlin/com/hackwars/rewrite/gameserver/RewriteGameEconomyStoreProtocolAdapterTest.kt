package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.BankTransactionResponse
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.DepositPayload
import com.hackwars.rewrite.gamecore.DeltaProjection
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.FacebookTransferPayload
import com.hackwars.rewrite.gamecore.FacebookWithdrawPayload
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PurchaseResponse
import com.hackwars.rewrite.gamecore.RequestPurchasePayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SellFileCommandPayload
import com.hackwars.rewrite.gamecore.SellFileMultiCommandPayload
import com.hackwars.rewrite.gamecore.SellFileMultiEntry
import com.hackwars.rewrite.gamecore.SellFileMultiResponse
import com.hackwars.rewrite.gamecore.SellFileResponse
import com.hackwars.rewrite.gamecore.StateSectionsDeltaProjection
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.TransferPayload
import com.hackwars.rewrite.gamecore.TransferResponse
import com.hackwars.rewrite.gamecore.WebsiteState
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteGameEconomyStoreProtocolAdapterTest {
    @Test
    fun depositWithdrawAndAliasWithdrawReturnCorrelatedResponsesWithEconomyDeltas() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")

        local.send(
            RewriteFrames.command(
                commandId = "deposit-1",
                commandName = "deposit",
                payload = RewriteGameJson.encode(
                    serializer = DepositPayload.serializer(),
                    value = DepositPayload(
                        amount = 600.0,
                        ip = "LOCAL-IP",
                        port = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val depositDelta = local.awaitFrame()
        val depositResponseFrame = local.awaitFrame()
        val depositResponse = RewriteGameJson.decode(
            serializer = BankTransactionResponse.serializer(),
            payload = depositResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("economy"), depositDelta.delta?.delta_keys)
        assertEquals(500.0, depositResponse.appliedAmount)
        assertEquals(0.0, depositResponse.pettyCashAfter)
        assertEquals(700.0, depositResponse.bankMoneyAfter)

        local.send(
            RewriteFrames.command(
                commandId = "withdraw-1",
                commandName = "facebookwithdraw",
                payload = RewriteGameJson.encode(
                    serializer = FacebookWithdrawPayload.serializer(),
                    value = FacebookWithdrawPayload(
                        amount = 150.0,
                        ip = "LOCAL-IP",
                        defaultPort = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val withdrawDelta = local.awaitFrame()
        val withdrawResponseFrame = local.awaitFrame()
        val withdrawResponse = RewriteGameJson.decode(
            serializer = BankTransactionResponse.serializer(),
            payload = withdrawResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("economy"), withdrawDelta.delta?.delta_keys)
        assertEquals("withdraw", withdrawResponse.operation)
        assertEquals(150.0, withdrawResponse.appliedAmount)
        assertEquals(150.0, withdrawResponse.pettyCashAfter)
        assertEquals(550.0, withdrawResponse.bankMoneyAfter)
    }

    @Test
    fun transferAndFacebookTransferFanOutToSourceAndTargetListeners() = runTest {
        val fixture = createFixture()
        val source = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        source.send(
            RewriteFrames.command(
                commandId = "transfer-1",
                commandName = "transfer",
                payload = RewriteGameJson.encode(
                    serializer = TransferPayload.serializer(),
                    value = TransferPayload(
                        amount = 125.0,
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                        port = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val sourceDelta = source.awaitFrame()
        val targetDelta = target.awaitFrame()
        val transferResponseFrame = source.awaitFrame()
        val transferResponse = RewriteGameJson.decode(
            serializer = TransferResponse.serializer(),
            payload = transferResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals("LOCAL-IP", sourceDelta.delta?.game_state_id)
        assertEquals("TARGET-IP", targetDelta.delta?.game_state_id)
        assertEquals(125.0, transferResponse.appliedAmount)
        assertEquals(375.0, transferResponse.sourcePettyCashAfter)
        assertEquals(175.0, transferResponse.targetPettyCashAfter)

        source.send(
            RewriteFrames.command(
                commandId = "transfer-2",
                commandName = "facebooktransfer",
                payload = RewriteGameJson.encode(
                    serializer = FacebookTransferPayload.serializer(),
                    value = FacebookTransferPayload(
                        amount = 50.0,
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                        defaultPort = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        source.awaitFrame()
        target.awaitFrame()
        val aliasResponseFrame = source.awaitFrame()
        val aliasResponse = RewriteGameJson.decode(
            serializer = TransferResponse.serializer(),
            payload = aliasResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(50.0, aliasResponse.appliedAmount)
        assertEquals(325.0, aliasResponse.sourcePettyCashAfter)
        assertEquals(225.0, aliasResponse.targetPettyCashAfter)
    }

    @Test
    fun transferCreditTriggersRecipientPassiveWatchWithSenderSourceIp() = runTest {
        val fixture = createFixture(
            targetState = targetState().copy(
                watches = WatchManagerState(
                    watches = listOf(
                        InstalledWatch(
                            kind = WatchKind.PETTY_CASH,
                            enabled = true,
                            note = "cash-watch",
                            cpuCost = 5.0,
                            quantityThreshold = 100.0,
                            baselineQuantity = 50.0,
                            installPort = 9,
                            contents = """
                                int main() {
                                    logMessage(getTargetIP());
                                    logMessage("" + getTransactionAmount());
                                    return 0;
                                }
                            """.trimIndent(),
                            compiledBinary = CompiledBinaryMetadata(
                                scriptFamily = com.hackwars.rewrite.gamecore.ScriptFamily.WATCH,
                                applicationKind = ApplicationKind.WATCH,
                                outputName = "watch.bin",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val source = fixture.authenticatedConnection("LOCAL-IP")
        val target = fixture.authenticatedConnection("TARGET-IP")

        source.send(
            RewriteFrames.command(
                commandId = "transfer-watch-1",
                commandName = "transfer",
                payload = RewriteGameJson.encode(
                    serializer = TransferPayload.serializer(),
                    value = TransferPayload(
                        amount = 125.0,
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                        port = 6,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        source.awaitFrame()
        val targetDelta = target.awaitFrame()
        source.awaitFrame()
        val targetProjection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = targetDelta.delta!!.payload.toByteArray(),
        )

        assertEquals(listOf("economy", "logs", "watches", "stats"), targetDelta.delta?.delta_keys)
        assertIs<StateSectionsDeltaProjection>(targetProjection)
        assertEquals(listOf("LOCAL-IP", "125"), targetProjection.logs?.entries?.map { it.renderedLine.substringAfterLast(' ') })
    }

    @Test
    fun sellFileAndSellFileMultiProduceFilesystemAndStoreDeltas() = runTest {
        val fixture = createFixture()
        val local = fixture.authenticatedConnection("LOCAL-IP")
        val store = fixture.authenticatedConnection("store1")

        local.send(
            RewriteFrames.command(
                commandId = "sell-1",
                commandName = "sellfile",
                payload = RewriteGameJson.encode(
                    serializer = SellFileCommandPayload.serializer(),
                    value = SellFileCommandPayload(
                        ip = "LOCAL-IP",
                        location = "/Store",
                        fileName = "local-merchant.bin",
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val sellDelta = local.awaitFrame()
        val sellResponseFrame = local.awaitFrame()
        val sellResponse = RewriteGameJson.decode(
            serializer = SellFileResponse.serializer(),
            payload = sellResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem"), sellDelta.delta?.delta_keys)
        assertEquals(196.0, sellResponse.file.price)

        local.send(
            RewriteFrames.command(
                commandId = "sell-multi-1",
                commandName = "sellfilemulti",
                payload = RewriteGameJson.encode(
                    serializer = SellFileMultiCommandPayload.serializer(),
                    value = SellFileMultiCommandPayload(
                        ip = "LOCAL-IP",
                        allFiles = listOf(
                            SellFileMultiEntry(
                                path = "/Public",
                                name = "rare.bin",
                                maker = "High",
                                quantity = 1,
                            ),
                        ),
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val localDelta = local.awaitFrame()
        val storeDelta = store.awaitFrame()
        val multiResponseFrame = local.awaitFrame()
        val multiResponse = RewriteGameJson.decode(
            serializer = SellFileMultiResponse.serializer(),
            payload = multiResponseFrame.command_response!!.payload.toByteArray(),
        )

        assertEquals(listOf("filesystem", "economy"), localDelta.delta?.delta_keys)
        assertEquals("store1", storeDelta.delta?.game_state_id)
        assertEquals(listOf("filesystem"), storeDelta.delta?.delta_keys)
        assertEquals(1500.0, multiResponse.creditedAmount)
    }

    @Test
    fun requestPurchaseUsesCanonicalStoreAliasAndUpdatesBuyerSellerAndRevenueListeners() = runTest {
        val fixture = createFixture()
        val buyer = fixture.authenticatedConnection("LOCAL-IP")
        val store = fixture.authenticatedConnection("store1")
        val revenue = fixture.authenticatedConnection("REV-IP")

        buyer.send(
            RewriteFrames.command(
                commandId = "purchase-1",
                commandName = "requestpurchase",
                payload = RewriteGameJson.encode(
                    serializer = RequestPurchasePayload.serializer(),
                    value = RequestPurchasePayload(
                        targetIp = "store999",
                        sourceIp = "LOCAL-IP",
                        fileName = "merchant.bin",
                        quantity = 2,
                    ),
                ),
                expectsResponse = true,
            ),
        )

        val storeDelta = store.awaitFrame()
        val buyerDelta = buyer.awaitFrame()
        val revenueDelta = revenue.awaitFrame()
        val responseFrame = buyer.awaitFrame()
        val response = RewriteGameJson.decode(
            serializer = PurchaseResponse.serializer(),
            payload = responseFrame.command_response!!.payload.toByteArray(),
        )
        val buyerProjection = RewriteGameJson.decode(
            serializer = DeltaProjection.serializer(),
            payload = buyerDelta.delta!!.payload.toByteArray(),
        )

        assertEquals("store1", storeDelta.delta?.game_state_id)
        assertEquals(listOf("filesystem"), storeDelta.delta?.delta_keys)
        assertEquals(listOf("filesystem", "economy"), buyerDelta.delta?.delta_keys)
        assertEquals("REV-IP", revenueDelta.delta?.game_state_id)
        assertEquals(listOf("economy"), revenueDelta.delta?.delta_keys)
        assertEquals("REV-IP", response.revenueTargetStateId.value)
        assertEquals(2, response.fulfilledQuantity)
        assertEquals(392.0, response.totalPrice)
        assertIs<StateSectionsDeltaProjection>(buyerProjection)
    }

    private fun TestScope.createFixture(
        localState: ComputerState = localState(),
        targetState: ComputerState = targetState(),
        revenueState: ComputerState = revenueState(),
        storeState: ComputerState = storeState(),
    ): Fixture {
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                GameStateId("LOCAL-IP") to localState,
                GameStateId("TARGET-IP") to targetState,
                GameStateId("REV-IP") to revenueState,
                GameStateId("store1") to storeState,
            ),
        )
        val interests = InMemoryInterestRegistry()
        val adapter = RewriteGameProtocolAdapter(
            dispatcher = DefaultCommandDispatcher(
                repository = repository,
                interestRegistry = interests,
            ),
            combatMaintenanceProgramRegistry = DisabledCombatMaintenanceProgramRegistry,
            ftpPasswordRepository = InMemoryFtpPasswordRepository(),
            interestRegistry = interests,
            serverId = "1",
            networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld("1"),
        )
        val harnessAdapter = HarnessBackedGameAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount(
                            playFabId = "PF-LOCALUSER",
                            playerIp = "LOCAL-IP",
                            sessionTicket = "SESSION-LOCALUSER",
                        ),
                        FakePlayerAccount(
                            playFabId = "PF-TARGETUSER",
                            playerIp = "TARGET-IP",
                            sessionTicket = "SESSION-TARGETUSER",
                        ),
                        FakePlayerAccount(
                            playFabId = "PF-REVUSER",
                            playerIp = "REV-IP",
                            sessionTicket = "SESSION-REVUSER",
                        ),
                        FakePlayerAccount(
                            playFabId = "PF-STOREUSER",
                            playerIp = "store1",
                            sessionTicket = "SESSION-STOREUSER",
                        ),
                    ),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)
        return Fixture(harness = harness)
    }

    private suspend fun Fixture.authenticatedConnection(requestedIp: String): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(authRequest(requestedIp))
        connection.awaitFrame()
        connection.awaitFrame()
        yield()
        connection.drainFrames()
        return connection
    }

    private fun authRequest(requestedIp: String): FrameEnvelope {
        return RewriteFrames.authRequest(
            service = RewriteService.GAME,
            sessionTicket = sessionTicketFor(requestedIp),
            clientBuild = "rewrite-it",
            playFabIdHint = playFabIdFor(requestedIp),
            requestedIp = requestedIp,
        )
    }

    private fun localState(): ComputerState {
        var filesystem = ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).filesystem
            .ensureDirectory("/Public")
            .ensureDirectory("/Store")
        filesystem = filesystem
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Store", "local-merchant.bin"),
                    name = "local-merchant.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    quantity = 3,
                    maker = "Medium",
                    compileCost = 100.0,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.GENERIC,
                        outputName = "local-merchant.bin",
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "rare.bin"),
                    name = "rare.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    quantity = 2,
                    maker = "High",
                    compileCost = 100.0,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.GENERIC,
                        outputName = "rare.bin",
                    ),
                ),
            )
        return ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).copy(
            economy = EconomyState(
                pettyCash = 500.0,
                bankMoney = 200.0,
                defaultBankPort = 6,
            ),
            filesystem = filesystem,
            ports = listOf(
                PortState(
                    number = 6,
                    type = "banking",
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                    ),
                ),
            ),
        )
    }

    private fun targetState(): ComputerState {
        return ComputerState.empty(GameStateId("TARGET-IP"), playerIp = "TARGET-IP").copy(
            economy = EconomyState(
                pettyCash = 50.0,
                bankMoney = 0.0,
                defaultBankPort = 9,
            ),
            ports = listOf(
                PortState(
                    number = 9,
                    type = "banking",
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                    ),
                ),
            ),
        )
    }

    private fun revenueState(): ComputerState {
        return ComputerState.empty(GameStateId("REV-IP"), playerIp = "REV-IP").copy(
            economy = EconomyState(pettyCash = 10.0),
        )
    }

    private fun storeState(): ComputerState {
        var filesystem = ComputerState.empty(GameStateId("store1"), playerIp = "store1").filesystem
            .ensureDirectory("/Store")
        filesystem = filesystem.saveFile(
            StoredFile(
                path = buildFilePath("/Store", "merchant.bin"),
                name = "merchant.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                quantity = 3,
                maker = "Medium",
                compileCost = 100.0,
                price = 196.0,
                compiledBinary = CompiledBinaryMetadata(
                    applicationKind = ApplicationKind.BANKING,
                    bankingApplication = true,
                    outputName = "merchant.bin",
                ),
            ),
        )
        return ComputerState.empty(GameStateId("store1"), playerIp = "store1").copy(
            filesystem = filesystem,
            website = WebsiteState(storeRevenueTargetStateId = GameStateId("REV-IP")),
        )
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )

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
                session = session.toEconomyGameSession(),
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
                session = session.toEconomyGameSession(),
                command = command,
                transport = GameConnectionTransport { connectionId, frame ->
                    harness.push(connectionId, frame)
                },
            )
        }
    }
}

private fun InMemoryAuthenticatedSession.toEconomyGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}

private fun sessionTicketFor(requestedIp: String): String = when (requestedIp) {
    "LOCAL-IP" -> "SESSION-LOCALUSER"
    "TARGET-IP" -> "SESSION-TARGETUSER"
    "REV-IP" -> "SESSION-REVUSER"
    "store1" -> "SESSION-STOREUSER"
    else -> "SESSION-LOCALUSER"
}

private fun playFabIdFor(requestedIp: String): String = when (requestedIp) {
    "LOCAL-IP" -> "PF-LOCALUSER"
    "TARGET-IP" -> "PF-TARGETUSER"
    "REV-IP" -> "PF-REVUSER"
    "store1" -> "PF-STOREUSER"
    else -> "PF-LOCALUSER"
}
