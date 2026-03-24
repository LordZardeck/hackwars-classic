package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.ComputerEvent
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.DirectoryCreatedEvent
import com.hackwars.rewrite.gamecore.DirectoryEntry
import com.hackwars.rewrite.gamecore.EquipmentInstalledEvent
import com.hackwars.rewrite.gamecore.EquipmentSlot
import com.hackwars.rewrite.gamecore.FileCompiledEvent
import com.hackwars.rewrite.gamecore.FileSavedEvent
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InstalledEquipment
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PreferenceSetEvent
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.SnapshotCoordinator
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.ApplicationInstalledEvent
import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.EconomyBalanceAdjustedEvent
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.PurchasedFileReceivedEvent
import com.hackwars.rewrite.gamecore.StoreFilePricedEvent
import com.hackwars.rewrite.gamecore.StoreFilesLiquidatedEvent
import com.hackwars.rewrite.gamecore.StoreInventoryReceivedEvent
import com.hackwars.rewrite.gamecore.StoreLiquidationLineItem
import com.hackwars.rewrite.gamecore.StoreListingPurchasedEvent
import com.hackwars.rewrite.gamecore.buildFilePath
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class JdbcComputerStateRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17").apply {
            withDatabaseName("rewrite_state_repository_test")
            withUsername("rewrite")
            withPassword("rewrite")
        }
    }

    private val serializer = ComputerStateSerializer()

    @Test
    fun appendsTypedEventsAndReplaysDeterministicState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
        )

        val updated = runBlockingAppend(repository, stateId, listOf(
            PreferenceSetEvent("show_clock", "true"),
            PreferenceSetEvent("show_logs", "false"),
        ))
        val reloaded = runBlockingLoad(repository, stateId)

        assertEquals(updated, reloaded)
        assertEquals(2, updated.version)
        assertEquals("true", updated.preferences.values["show_clock"])
        assertEquals("false", updated.preferences.values["show_logs"])
        assertEquals(2, countRows("rewrite_state_event"))
    }

    @Test
    fun replaysFilesystemAndInstallEventsIntoDeterministicTypedState() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(
                id = stateId,
                playFabId = "PF-LOCALUSER",
            ).copy(
                economy = ComputerState.empty(id = stateId).economy.copy(pettyCash = 500.0),
            ),
        )
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        val sourceFile = StoredFile(
            path = buildFilePath("/Public", "bank.hws"),
            name = "bank.hws",
            kind = StoredFileKind.SCRIPT_SOURCE,
            contents = "bank script",
            compileCost = 75.0,
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.BANKING,
                outputName = "bank.bin",
                applicationKind = ApplicationKind.BANKING,
                bankingApplication = true,
                experienceAward = 4,
            ),
        )
        val compiledFile = sourceFile.copy(
            path = buildFilePath("/Public", "bank.bin"),
            name = "bank.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
        )
        val equipmentFile = StoredFile(
            path = buildFilePath("/Public", "cpu-card.bin"),
            name = "cpu-card.bin",
            kind = StoredFileKind.EQUIPMENT_BINARY,
            contents = "cpu boost",
            compiledBinary = CompiledBinaryMetadata(
                scriptFamily = ScriptFamily.GENERAL,
                equipmentSlot = EquipmentSlot.CPU,
                outputName = "cpu-card.bin",
            ),
        )

        runBlockingAppend(
            repository,
            stateId,
            listOf(
                DirectoryCreatedEvent(DirectoryEntry(path = "/Public", name = "Public")),
                FileSavedEvent(sourceFile),
                FileSavedEvent(equipmentFile),
                FileCompiledEvent(
                    sourceFilePath = sourceFile.path,
                    remainingSourceFile = sourceFile,
                    compiledFile = compiledFile,
                    pettyCashDelta = -75.0,
                    scriptFamily = ScriptFamily.BANKING,
                    experienceDelta = 4,
                ),
                ApplicationInstalledEvent(
                    sourceFilePath = compiledFile.path,
                    remainingSourceFile = null,
                    portState = PortState(
                        number = 6,
                        type = "banking",
                        installedApplication = InstalledApplication(
                            name = "bank.bin",
                            kind = ApplicationKind.BANKING,
                            binaryPath = compiledFile.path,
                            banking = true,
                        ),
                    ),
                    defaultBankPort = 6,
                ),
                EquipmentInstalledEvent(
                    sourceFilePath = equipmentFile.path,
                    remainingSourceFile = null,
                    slot = EquipmentSlot.CPU,
                    equipment = InstalledEquipment(
                        slot = EquipmentSlot.CPU,
                        name = "cpu-card.bin",
                        binaryPath = equipmentFile.path,
                    ),
                ),
            ),
        )

        val reloaded = runBlockingLoad(repository, stateId)

        requireNotNull(reloaded)
        assertEquals(6, reloaded.economy.defaultBankPort)
        assertEquals(425.0, reloaded.economy.pettyCash)
        assertEquals(4, reloaded.stats.experienceByFamily[ScriptFamily.BANKING])
        assertEquals(1, reloaded.filesystem.directoriesByPath.count { it.key == "/Public" })
        assertEquals("bank.hws", reloaded.filesystem.filesByPath[sourceFile.path]?.name)
        assertEquals("bank.bin", reloaded.ports.single { it.number == 6 }.installedApplication?.name)
        assertEquals("cpu-card.bin", reloaded.hardware.equipmentSlots[EquipmentSlot.CPU]?.name)
        assertTrue(countRows("rewrite_state_snapshot") >= 1)
    }

    @Test
    fun snapshotsAfterEventOrTimeThresholds() {
        resetDatabase()
        val stateId = GameStateId("LOCAL-IP")
        seedPlayerAndComputer(
            stateId = stateId,
            state = ComputerState.empty(id = stateId, playFabId = "PF-LOCALUSER"),
        )
        var now = Instant.parse("2026-03-24T12:00:00Z")
        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(
                eventThreshold = 2,
                timeThreshold = 5.seconds,
            ),
            clock = { now },
        )

        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_clock", "true")))
        assertEquals(0, countRows("rewrite_state_snapshot"))

        now = now.plusSeconds(6)
        runBlockingAppend(repository, stateId, listOf(PreferenceSetEvent("show_logs", "false")))
        assertEquals(1, countRows("rewrite_state_snapshot"))
    }

    @Test
    fun replaysEconomyAndStoreEventsAcrossBuyerSellerRevenueAndShardStore() {
        resetDatabase()
        val buyerId = GameStateId("BUYER-IP")
        val sellerId = GameStateId("SELLER-IP")
        val revenueId = GameStateId("REV-IP")
        val storeId = GameStateId("store1")
        seedPlayerAndComputer(
            stateId = buyerId,
            state = ComputerState.empty(id = buyerId, playFabId = "PF-BUYER").copy(
                economy = ComputerState.empty(id = buyerId).economy.copy(
                    pettyCash = 1000.0,
                    bankMoney = 100.0,
                    defaultBankPort = 6,
                ),
            ),
            playerId = "buyer-user",
        )
        seedPlayerAndComputer(
            stateId = sellerId,
            state = ComputerState.empty(id = sellerId, playFabId = "PF-SELLER").copy(
                economy = ComputerState.empty(id = sellerId).economy.copy(pettyCash = 400.0),
            ),
            playerId = "seller-user",
        )
        seedPlayerAndComputer(
            stateId = revenueId,
            state = ComputerState.empty(id = revenueId, playFabId = "PF-REV").copy(
                economy = ComputerState.empty(id = revenueId).economy.copy(pettyCash = 10.0),
            ),
            playerId = "revenue-user",
        )
        seedPlayerAndComputer(
            stateId = storeId,
            state = ComputerState.empty(id = storeId, playFabId = "PF-STORE"),
            playerId = "store-user",
        )

        val repository = JdbcComputerStateRepository(
            connectionFactory = ::newConnection,
            serializer = serializer,
            snapshotCoordinator = SnapshotCoordinator(eventThreshold = 1, timeThreshold = 5.seconds),
        )

        val pricedListing = StoredFile(
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
        )
        val shardCopy = StoredFile(
            path = buildFilePath("/Store", "rare.bin"),
            name = "rare.bin",
            kind = StoredFileKind.APPLICATION_BINARY,
            quantity = 1,
            maker = "High",
            price = 1500.0,
            compiledBinary = CompiledBinaryMetadata(
                applicationKind = ApplicationKind.GENERIC,
                outputName = "rare.bin",
            ),
        )

        runBlockingAppend(
            repository,
            sellerId,
            listOf(
                FileSavedEvent(pricedListing),
                StoreFilePricedEvent(
                    filePath = pricedListing.path,
                    price = 196.0,
                ),
                StoreFilesLiquidatedEvent(
                    soldItems = listOf(
                        StoreLiquidationLineItem(
                            sourceFilePath = buildFilePath("/Public", "rare.bin"),
                            remainingSourceFile = StoredFile(
                                path = buildFilePath("/Public", "rare.bin"),
                                name = "rare.bin",
                                kind = StoredFileKind.APPLICATION_BINARY,
                                quantity = 1,
                                maker = "High",
                                compiledBinary = CompiledBinaryMetadata(
                                    applicationKind = ApplicationKind.GENERIC,
                                    outputName = "rare.bin",
                                ),
                            ),
                            creditedPettyCash = 1500.0,
                        ),
                    ),
                ),
                StoreListingPurchasedEvent(
                    listingPath = pricedListing.path,
                    remainingListing = pricedListing.copy(quantity = 1),
                ),
            ),
        )
        runBlockingAppend(
            repository,
            buyerId,
            listOf(
                PurchasedFileReceivedEvent(
                    file = pricedListing.copy(
                        path = buildFilePath("/", "merchant.bin"),
                        quantity = 2,
                    ),
                    pettyCashDelta = -392.0,
                ),
            ),
        )
        runBlockingAppend(
            repository,
            revenueId,
            listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = 392.0),
            ),
        )
        runBlockingAppend(
            repository,
            storeId,
            listOf(
                StoreInventoryReceivedEvent(files = listOf(shardCopy)),
            ),
        )

        val buyer = runBlockingLoad(repository, buyerId)
        val seller = runBlockingLoad(repository, sellerId)
        val revenue = runBlockingLoad(repository, revenueId)
        val store = runBlockingLoad(repository, storeId)

        requireNotNull(buyer)
        requireNotNull(seller)
        requireNotNull(revenue)
        requireNotNull(store)
        assertEquals(608.0, buyer.economy.pettyCash)
        assertEquals(2, buyer.filesystem.filesByPath["/merchant.bin"]?.quantity)
        assertEquals(1900.0, seller.economy.pettyCash)
        assertEquals(1, seller.filesystem.filesByPath["/Store/merchant.bin"]?.quantity)
        assertEquals(402.0, revenue.economy.pettyCash)
        assertEquals(1, store.filesystem.filesByPath["/Store/rare.bin"]?.quantity)
        assertTrue(countRows("rewrite_state_snapshot") >= 4)
    }

    private fun resetDatabase() {
        newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop schema if exists public cascade")
                statement.execute("create schema public")
            }
            RewriteLiquibase.update(connection)
        }
    }

    private fun seedPlayerAndComputer(
        stateId: GameStateId,
        state: ComputerState,
        playerId: String = "local-user",
    ) {
        newConnection().use { connection ->
            connection.prepareStatement(
                """
                insert into rewrite_player_account(player_id, playfab_id, player_ip, account_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, playerId)
                statement.setString(2, state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" })
                statement.setString(3, state.identity.playerIp.ifBlank { stateId.value })
                statement.setString(
                    4,
                    """{"playerId":"$playerId","playFabId":"${state.identity.playFabId.ifBlank { "PF-${playerId.uppercase()}" }}","playerIp":"${state.identity.playerIp.ifBlank { stateId.value }}"}""",
                )
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into rewrite_computer_state(computer_id, player_id, ip_address, state_payload)
                values (?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, stateId.value)
                statement.setString(2, playerId)
                statement.setString(3, stateId.value)
                statement.setString(4, serializer.encodeStateJson(state))
                statement.executeUpdate()
            }
        }
    }

    private fun countRows(tableName: String): Int {
        newConnection().use { connection ->
            connection.prepareStatement("select count(*) from $tableName").use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt(1)
                }
            }
        }
    }

    private fun runBlockingAppend(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
        events: List<ComputerEvent>,
    ): ComputerState {
        return kotlinx.coroutines.runBlocking {
            repository.appendEvents(stateId, events)
        }
    }

    private fun runBlockingLoad(
        repository: JdbcComputerStateRepository,
        stateId: GameStateId,
    ): ComputerState? {
        return kotlinx.coroutines.runBlocking {
            repository.load(stateId)
        }
    }

    private fun newConnection(): Connection {
        return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }
}
