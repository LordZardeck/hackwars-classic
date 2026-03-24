package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EconomyStoreCommandsTest {
    @Test
    fun depositAndWithdrawClampAndRequireValidBankPort() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to bankingPlayer(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val deposit = dispatcher.request(
            command = DepositCommand(
                stateId = stateId,
                amount = 750.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val withdraw = dispatcher.request(
            command = WithdrawCommand(
                stateId = stateId,
                amount = 250.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val finalState = repository.load(stateId)

        requireNotNull(finalState)
        assertEquals(500.0, deposit.appliedAmount)
        assertEquals(0.0, deposit.pettyCashAfter)
        assertEquals(700.0, deposit.bankMoneyAfter)
        assertEquals(250.0, withdraw.appliedAmount)
        assertEquals(250.0, withdraw.pettyCashAfter)
        assertEquals(450.0, withdraw.bankMoneyAfter)
        assertEquals(250.0, finalState.economy.pettyCash)
        assertEquals(450.0, finalState.economy.bankMoney)
        assertEquals(listOf(setOf("economy"), setOf("economy")), publisher.deltas.map { it.second.deltaKeys })

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = DepositCommand(
                    stateId = stateId,
                    amount = 50.0,
                    portNumber = 99,
                ),
                metadata = CommandMetadata(connectionId = "conn-1"),
                publisher = publisher,
            )
        }
    }

    @Test
    fun transferMovesMoneyAndRejectsTargetsWithoutActiveDefaultBank() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val badTargetId = GameStateId("BAD-TARGET")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to bankingPlayer(sourceId),
                targetId to receivingPlayer(targetId),
                badTargetId to ComputerState.empty(badTargetId, playerIp = badTargetId.value),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("source-conn", sourceId)
        interests.register("target-conn", targetId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = TransferCommand(
                sourceStateId = sourceId,
                targetStateId = targetId,
                amount = 125.0,
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "source-conn"),
            publisher = publisher,
        )

        assertEquals(125.0, response.appliedAmount)
        assertEquals(375.0, response.sourcePettyCashAfter)
        assertEquals(175.0, response.targetPettyCashAfter)
        assertEquals(
            listOf(setOf("economy"), setOf("economy")),
            publisher.deltas.map { it.second.deltaKeys },
        )
        assertEquals(setOf("source-conn"), publisher.deltas[0].first)
        assertEquals(setOf("target-conn"), publisher.deltas[1].first)

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = TransferCommand(
                    sourceStateId = sourceId,
                    targetStateId = badTargetId,
                    amount = 50.0,
                    portNumber = 6,
                ),
                metadata = CommandMetadata(connectionId = "source-conn"),
            )
        }
    }

    @Test
    fun sellFilePricesOneLocalFileInPlaceWithoutMovingIt() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to sellerPlayer(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = SellFileCommand(
                stateId = stateId,
                path = "/Store",
                fileName = "merchant.bin",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val state = repository.load(stateId)

        requireNotNull(state)
        assertEquals("/Store/merchant.bin", response.file.path)
        assertEquals(196.0, response.file.price)
        assertEquals(196.0, state.filesystem.filesByPath["/Store/merchant.bin"]?.price)
        assertEquals(setOf("filesystem"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun sellFileMultiLiquidatesLocalFilesToShardStoreAndCreditsPettyCash() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val storeStateId = GameStateId("store1")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to sellerPlayer(stateId),
                storeStateId to shardStore(storeStateId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("seller-conn", stateId)
        interests.register("store-conn", storeStateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = SellFileMultiCommand(
                stateId = stateId,
                storeStateId = storeStateId,
                entries = listOf(
                    SellFileMultiEntry(
                        path = "/Public",
                        name = "rare.bin",
                        maker = "High",
                        quantity = 1,
                    ),
                ),
            ),
            metadata = CommandMetadata(connectionId = "seller-conn"),
            publisher = publisher,
        )
        val sellerState = repository.load(stateId)
        val storeState = repository.load(storeStateId)

        requireNotNull(sellerState)
        requireNotNull(storeState)
        assertEquals(1500.0, response.creditedAmount)
        assertEquals(1, sellerState.filesystem.filesByPath["/Public/rare.bin"]?.quantity)
        assertEquals(1900.0, sellerState.economy.pettyCash)
        assertEquals(1, storeState.filesystem.filesByPath["/Store/rare.bin"]?.quantity)
        assertEquals(1500.0, storeState.filesystem.filesByPath["/Store/rare.bin"]?.price)
        assertEquals(listOf(setOf("filesystem", "economy"), setOf("filesystem")), publisher.deltas.map { it.second.deltaKeys })
    }

    @Test
    fun requestPurchaseMovesInventoryAndCreditsExplicitRevenueTarget() = runTest {
        val buyerId = GameStateId("BUYER-IP")
        val sellerId = GameStateId("SELLER-IP")
        val revenueId = GameStateId("REV-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                buyerId to buyerPlayer(buyerId),
                sellerId to sellerPlayer(sellerId).copy(
                    website = WebsiteState(storeRevenueTargetStateId = revenueId),
                ),
                revenueId to ComputerState.empty(revenueId, playerIp = revenueId.value).copy(
                    economy = EconomyState(pettyCash = 10.0),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("buyer-conn", buyerId)
        interests.register("seller-conn", sellerId)
        interests.register("revenue-conn", revenueId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestPurchaseCommand(
                buyerStateId = buyerId,
                sellerStateId = sellerId,
                fileName = "merchant.bin",
                requestedQuantity = 2,
            ),
            metadata = CommandMetadata(connectionId = "buyer-conn"),
            publisher = publisher,
        )
        val buyerState = repository.load(buyerId)
        val sellerState = repository.load(sellerId)
        val revenueState = repository.load(revenueId)

        requireNotNull(buyerState)
        requireNotNull(sellerState)
        requireNotNull(revenueState)
        assertEquals(2, response.fulfilledQuantity)
        assertEquals(392.0, response.totalPrice)
        assertEquals("REV-IP", response.revenueTargetStateId.value)
        assertEquals(608.0, buyerState.economy.pettyCash)
        assertEquals(402.0, revenueState.economy.pettyCash)
        assertEquals(2, buyerState.filesystem.filesByPath["/merchant.bin"]?.quantity)
        assertEquals(1, sellerState.filesystem.filesByPath["/Store/merchant.bin"]?.quantity)
        assertEquals(
            listOf(setOf("filesystem"), setOf("filesystem", "economy"), setOf("economy")),
            publisher.deltas.map { it.second.deltaKeys },
        )
    }

    @Test
    fun requestPurchaseRejectsSelfPurchaseInsufficientFundsAndCapacityWithoutMutation() = runTest {
        val buyerId = GameStateId("BUYER-IP")
        val fullBuyerId = GameStateId("FULL-BUYER")
        val sellerId = GameStateId("SELLER-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                buyerId to buyerPlayer(buyerId).copy(
                    economy = EconomyState(pettyCash = 10.0, bankMoney = 0.0, defaultBankPort = 6),
                ),
                fullBuyerId to buyerPlayer(fullBuyerId).copy(
                    economy = EconomyState(pettyCash = 1000.0, bankMoney = 0.0, defaultBankPort = 6),
                    hardware = HardwareState(hdMaximum = 1),
                    filesystem = buyerPlayer(fullBuyerId).filesystem.saveFile(
                        StoredFile(
                            path = buildFilePath("/", "occupied.txt"),
                            name = "occupied.txt",
                            kind = StoredFileKind.TEXT,
                            contents = "already here",
                        ),
                    ),
                ),
                sellerId to sellerPlayer(sellerId),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(repository, InMemoryInterestRegistry())

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = RequestPurchaseCommand(
                    buyerStateId = sellerId,
                    sellerStateId = sellerId,
                    fileName = "merchant.bin",
                    requestedQuantity = 1,
                ),
                metadata = CommandMetadata(connectionId = "seller-conn"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = RequestPurchaseCommand(
                    buyerStateId = buyerId,
                    sellerStateId = sellerId,
                    fileName = "merchant.bin",
                    requestedQuantity = 1,
                ),
                metadata = CommandMetadata(connectionId = "buyer-conn"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = RequestPurchaseCommand(
                    buyerStateId = fullBuyerId,
                    sellerStateId = sellerId,
                    fileName = "merchant.bin",
                    requestedQuantity = 1,
                ),
                metadata = CommandMetadata(connectionId = "buyer-conn"),
            )
        }

        val unchangedBuyer = repository.load(buyerId)
        val unchangedFullBuyer = repository.load(fullBuyerId)
        requireNotNull(unchangedBuyer)
        requireNotNull(unchangedFullBuyer)
        assertTrue(unchangedBuyer.filesystem.filesByPath.isEmpty())
        assertEquals(10.0, unchangedBuyer.economy.pettyCash)
        assertTrue(unchangedFullBuyer.filesystem.filesByPath.containsKey("/occupied.txt"))
        assertEquals(1000.0, unchangedFullBuyer.economy.pettyCash)
    }

    private fun bankingPlayer(stateId: GameStateId): ComputerState {
        return ComputerState.empty(stateId, playFabId = "PF-${stateId.value}", playerIp = stateId.value).copy(
            economy = EconomyState(
                pettyCash = 500.0,
                bankMoney = 200.0,
                defaultBankPort = 6,
            ),
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

    private fun receivingPlayer(stateId: GameStateId): ComputerState {
        return ComputerState.empty(stateId, playFabId = "PF-${stateId.value}", playerIp = stateId.value).copy(
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

    private fun buyerPlayer(stateId: GameStateId): ComputerState {
        return bankingPlayer(stateId).copy(
            economy = EconomyState(
                pettyCash = 1000.0,
                bankMoney = 100.0,
                defaultBankPort = 6,
            ),
            filesystem = ComputerState.empty(stateId, playerIp = stateId.value).filesystem.ensureDirectory("/Public"),
        )
    }

    private fun sellerPlayer(stateId: GameStateId): ComputerState {
        var filesystem = ComputerState.empty(stateId, playerIp = stateId.value).filesystem
            .ensureDirectory("/Public")
            .ensureDirectory("/Store")
        filesystem = filesystem
            .saveFile(
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
        return ComputerState.empty(stateId, playFabId = "PF-${stateId.value}", playerIp = stateId.value).copy(
            economy = EconomyState(pettyCash = 400.0),
            filesystem = filesystem,
        )
    }

    private fun shardStore(stateId: GameStateId): ComputerState {
        return ComputerState.empty(stateId, playerIp = stateId.value).copy(
            filesystem = ComputerState.empty(stateId, playerIp = stateId.value).filesystem.ensureDirectory("/Store"),
        )
    }
}
