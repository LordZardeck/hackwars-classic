package com.hackwars.rewrite.clientdev

import com.hackwars.rewrite.client.DeterministicRewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteGameConnectionConfig
import com.hackwars.rewrite.client.RewriteLoginAuthGateway
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteServiceSession
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.CommandDispatcher
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerLogEntry
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.CoroutineProgramScheduler
import com.hackwars.rewrite.gamecore.DefaultCommandDispatcher
import com.hackwars.rewrite.gamecore.EquipmentSlot
import com.hackwars.rewrite.gamecore.FilesystemState
import com.hackwars.rewrite.gamecore.FirewallActionProfile
import com.hackwars.rewrite.gamecore.FirewallCombatProfile
import com.hackwars.rewrite.gamecore.FirewallKind
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.HardwareState
import com.hackwars.rewrite.gamecore.InMemoryAttackProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryCombatMaintenanceProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository
import com.hackwars.rewrite.gamecore.InMemoryDailyIncomeProgramRegistry
import com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository
import com.hackwars.rewrite.gamecore.InMemoryInterestRegistry
import com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.InstalledEquipment
import com.hackwars.rewrite.gamecore.InstalledFirewall
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.LogState
import com.hackwars.rewrite.gamecore.MaliciousProgramConfig
import com.hackwars.rewrite.gamecore.NetworkState
import com.hackwars.rewrite.gamecore.NpcCategory
import com.hackwars.rewrite.gamecore.NpcDirectoryEntry
import com.hackwars.rewrite.gamecore.NoOpHookSideEffectSink
import com.hackwars.rewrite.gamecore.NoOpHttpHookRuntime
import com.hackwars.rewrite.gamecore.PlayerStatsState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PreferenceState
import com.hackwars.rewrite.gamecore.ProgramScriptBundle
import com.hackwars.rewrite.gamecore.ProgramScriptSlot
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.RuntimeState
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.SearchCatalogRepository
import com.hackwars.rewrite.gamecore.SearchableWebsiteDocument
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WebsiteState
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.parentDirectoryOf
import com.hackwars.rewrite.gameserver.AuthenticatedGameSession
import com.hackwars.rewrite.gameserver.GameConnectionTransport
import com.hackwars.rewrite.gameserver.RewriteGameProtocolAdapter
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import com.hackwars.rewrite.testkit.FakePlayerAccount
import com.hackwars.rewrite.testkit.FakeSessionCatalog
import com.hackwars.rewrite.testkit.FakeSessionTicketVerifier
import com.hackwars.rewrite.testkit.InMemoryAuthenticatedSession
import com.hackwars.rewrite.testkit.InMemoryClientConnection
import com.hackwars.rewrite.testkit.InMemoryRewriteServiceHarness
import com.hackwars.rewrite.testkit.RewriteServiceAdapter
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val SERVER_ID = "1"
private const val LOCAL_PLAYER_IP = "192.0.2.10"
private const val TARGET_PLAYER_IP = "198.51.100.20"
private const val ZOMBIE_PLAYER_IP = "203.0.113.30"
private const val STORE_PLAYER_IP = "198.51.100.40"
private const val LOCAL_PLAYFAB_ID = "PF-LOCALUSER"
private const val TARGET_PLAYFAB_ID = "PF-TARGET"
private const val ZOMBIE_PLAYFAB_ID = "PF-ZOMBIE"
private const val STORE_PLAYFAB_ID = "PF-STORE"
private const val LOCAL_SESSION_TICKET = "SESSION-LOCALUSER"
private const val TARGET_SESSION_TICKET = "SESSION-TARGET"
private const val ZOMBIE_SESSION_TICKET = "SESSION-ZOMBIE"
private const val STORE_SESSION_TICKET = "SESSION-STORE"

class RewriteClientDevEnvironment(
    private val clock: () -> Instant = { Instant.now() },
    runtimeScope: CoroutineScope? = null,
) : AutoCloseable {
    private val ownsRuntimeScope = runtimeScope == null
    private val appScope = runtimeScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clockMillis: () -> Long = { clock().toEpochMilli() }
    private val repository = InMemoryComputerStateRepository(seededStates())
    private val interestRegistry = InMemoryInterestRegistry()
    private val attackProgramRegistry = InMemoryAttackProgramRegistry()
    private val dailyIncomeProgramRegistry = InMemoryDailyIncomeProgramRegistry()
    private val combatMaintenanceProgramRegistry = InMemoryCombatMaintenanceProgramRegistry()
    private val networkDirectoryRepository = InMemoryNetworkDirectoryRepository.defaultWorld(SERVER_ID)
    private val dispatcher = createDispatcher()
    private val protocolAdapter = RewriteGameProtocolAdapter(
        dispatcher = dispatcher,
        interestRegistry = interestRegistry,
        serverId = SERVER_ID,
        clock = clockMillis,
        httpHookRuntime = NoOpHttpHookRuntime,
        hookSideEffectSink = NoOpHookSideEffectSink,
        networkDirectoryRepository = networkDirectoryRepository,
        searchCatalogRepository = EmptySearchCatalogRepository,
        ftpPasswordRepository = InMemoryFtpPasswordRepository(),
        attackProgramRegistry = attackProgramRegistry,
        dailyIncomeProgramRegistry = dailyIncomeProgramRegistry,
        combatMaintenanceProgramRegistry = combatMaintenanceProgramRegistry,
    )
    private val harnessAdapter = HarnessBackedGameAdapter(protocolAdapter)
    private val harness = InMemoryRewriteServiceHarness(
        adapter = harnessAdapter,
        verifier = FakeSessionTicketVerifier(
            catalog = FakeSessionCatalog(
                accounts = listOf(
                    FakePlayerAccount(
                        playFabId = LOCAL_PLAYFAB_ID,
                        playerIp = LOCAL_PLAYER_IP,
                        sessionTicket = LOCAL_SESSION_TICKET,
                    ),
                    FakePlayerAccount(
                        playFabId = TARGET_PLAYFAB_ID,
                        playerIp = TARGET_PLAYER_IP,
                        sessionTicket = TARGET_SESSION_TICKET,
                    ),
                    FakePlayerAccount(
                        playFabId = ZOMBIE_PLAYFAB_ID,
                        playerIp = ZOMBIE_PLAYER_IP,
                        sessionTicket = ZOMBIE_SESSION_TICKET,
                    ),
                    FakePlayerAccount(
                        playFabId = STORE_PLAYFAB_ID,
                        playerIp = STORE_PLAYER_IP,
                        sessionTicket = STORE_SESSION_TICKET,
                    ),
                ),
            ),
            clock = clock,
        ),
        scope = appScope,
        clock = clock,
    )
    private val harnessSessionGateway = HarnessBackedRewriteServiceSessionGateway(
        harness = harness,
        runtimeScope = appScope,
    )

    val authGateway: RewriteLoginAuthGateway = DeterministicRewriteLoginAuthGateway()
    val sessionGateway: RewriteServiceSessionGateway = harnessSessionGateway

    init {
        harnessAdapter.attachHarness(harness)
    }

    fun createController(
        workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ): RewriteRootController {
        return RewriteRootController(
            gameConnectionConfig = RewriteGameConnectionConfig(clientBuild = "rewrite-client-dev"),
            authGateway = authGateway,
            sessionGateway = sessionGateway,
            clock = clock,
            workerScope = workerScope,
        )
    }

    internal suspend fun authenticatedConnection(
        requestedIp: String = LOCAL_PLAYER_IP,
    ): InMemoryClientConnection {
        val connection = harness.connect("rewrite-client-dev-game")
        val account = sessionAccountFor(requestedIp)
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = account.sessionTicket,
                clientBuild = "rewrite-client-dev",
                playFabIdHint = account.playFabId,
                requestedIp = account.playerIp,
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        return connection
    }

    override fun close() {
        harnessSessionGateway.close()
        if (ownsRuntimeScope) {
            appScope.cancel()
        }
    }

    private fun createDispatcher(): CommandDispatcher {
        return DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interestRegistry,
            attackProgramRegistry = attackProgramRegistry,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interestRegistry,
                coroutineScope = appScope,
            ),
        )
    }

    private fun sessionAccountFor(playerIp: String): FakePlayerAccount {
        return when (playerIp) {
            LOCAL_PLAYER_IP -> FakePlayerAccount(LOCAL_PLAYFAB_ID, LOCAL_PLAYER_IP, LOCAL_SESSION_TICKET)
            TARGET_PLAYER_IP -> FakePlayerAccount(TARGET_PLAYFAB_ID, TARGET_PLAYER_IP, TARGET_SESSION_TICKET)
            ZOMBIE_PLAYER_IP -> FakePlayerAccount(ZOMBIE_PLAYFAB_ID, ZOMBIE_PLAYER_IP, ZOMBIE_SESSION_TICKET)
            STORE_PLAYER_IP -> FakePlayerAccount(STORE_PLAYFAB_ID, STORE_PLAYER_IP, STORE_SESSION_TICKET)
            else -> error("No deterministic session is seeded for $playerIp.")
        }
    }

    private fun seededStates(): Map<GameStateId, ComputerState> {
        val localStateId = GameStateId(LOCAL_PLAYER_IP)
        val targetStateId = GameStateId(TARGET_PLAYER_IP)
        val zombieStateId = GameStateId(ZOMBIE_PLAYER_IP)
        val storeStateId = GameStateId(STORE_PLAYER_IP)

        val localFiles = buildLocalFiles()
        val targetFiles = buildTargetFiles()
        val storeFiles = buildStoreFiles()

        val localState = ComputerState.empty(
            id = localStateId,
            playFabId = LOCAL_PLAYFAB_ID,
            playerIp = LOCAL_PLAYER_IP,
            displayName = "Local User",
        ).copy(
            economy = com.hackwars.rewrite.gamecore.EconomyState(
                pettyCash = 500.0,
                bankMoney = 1_250.0,
                commodities = listOf(5.0, 3.0, 2.0, 1.0, 1.0),
                defaultBankPort = 1,
                defaultRedirectPort = 6,
            ),
            hardware = HardwareState(
                cpuType = 3,
                cpuMax = 120.0,
                memoryType = 3,
                hdType = 2,
                hdQuantity = localFiles.size,
                hdMaximum = 200,
                equipmentSlots = linkedMapOf(
                    EquipmentSlot.CPU to InstalledEquipment(
                        slot = EquipmentSlot.CPU,
                        name = "Installed CPU Boost",
                        maker = "Medium",
                        binaryPath = buildFilePath("/Software/Binaries", "cpu_boost.bin"),
                        cpuBoost = 12.0,
                        healCostMultiplier = 0.8,
                        healModifierDelta = -1,
                    ),
                    EquipmentSlot.PCI to InstalledEquipment(
                        slot = EquipmentSlot.PCI,
                        name = "Installed PCI Watch",
                        maker = "Low",
                        binaryPath = buildFilePath("/Software/Binaries", "pci_watch.bin"),
                        watchCapacityBoost = 2,
                    ),
                ),
            ),
            ports = localPorts(),
            network = NetworkState(
                currentNetworkName = ROOT_NETWORK_NAME,
                storeStateId = storeStateId,
                allowedNetworks = setOf("ProgNet"),
                regularNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = targetStateId,
                        displayName = "Target Commerce Hub",
                        title = "Player Target",
                        category = NpcCategory.REGULAR,
                    ),
                ),
            ),
            watches = WatchManagerState(
                watches = listOf(
                    InstalledWatch(
                        kind = WatchKind.HEALTH,
                        enabled = true,
                        note = "Health Watch",
                        cpuCost = 5.0,
                        quantityThreshold = 65.0,
                        baselineQuantity = 100.0,
                        installPort = 1,
                        searchFirewallType = 1,
                        observedPorts = listOf(5, 6, 7),
                        contents = renderScriptContents(watchScriptBundle()),
                        scriptBundle = watchScriptBundle(),
                        compiledBinary = watchBinaryMetadata("watch.bin"),
                    ),
                    InstalledWatch(
                        kind = WatchKind.SCAN,
                        enabled = false,
                        note = "Scan Watch",
                        cpuCost = 3.0,
                        installPort = 7,
                        searchFirewallType = 2,
                        contents = renderScriptContents(watchScriptBundle()),
                        scriptBundle = watchScriptBundle(),
                        compiledBinary = watchBinaryMetadata("watch.bin"),
                    ),
                ),
            ),
            filesystem = filesystemState(
                currentPath = "/",
                files = localFiles,
            ),
            website = WebsiteState(
                title = "Local Development Site",
                body = """
                    <html>
                    <body>
                    <h1>Rewrite Client Dev Mode</h1>
                    <p>This deterministic desktop is backed by the real rewrite GAME adapter.</p>
                    <p>Try 198.51.100.20 for scans, attacks, Public FTP, and browser navigation.</p>
                    </body>
                    </html>
                """.trimIndent(),
                voteCount = 7,
                votesAvailable = 3,
                storeRevenueTargetStateId = localStateId,
            ),
            preferences = PreferenceState(
                values = linkedMapOf(
                    "network" to "true",
                    "logwindow" to "true",
                    "attacktutorial" to "true",
                    "shellpopup" to "ask",
                ),
            ),
            stats = PlayerStatsState(
                experienceByFamily = linkedMapOf(
                    ScriptFamily.BANKING to 250.0,
                    ScriptFamily.ATTACK to 400.0,
                    ScriptFamily.HTTP to 125.0,
                    ScriptFamily.WATCH to 90.0,
                ),
                totalLevel = 12,
                noobProtectionLevel = 1,
            ),
            logs = LogState(
                entries = listOf(
                    ComputerLogEntry(
                        createdAtEpochMillis = clockMillis() - 5_000L,
                        renderedLine = "Boot complete on $LOCAL_PLAYER_IP",
                        sourceIp = LOCAL_PLAYER_IP,
                    ),
                    ComputerLogEntry(
                        createdAtEpochMillis = clockMillis() - 3_000L,
                        renderedLine = "Deterministic rewrite dev session ready.",
                        sourceIp = LOCAL_PLAYER_IP,
                    ),
                ),
            ),
            runtime = RuntimeState(currentCpuLoad = 10.0),
        )

        val targetState = ComputerState.empty(
            id = targetStateId,
            playFabId = TARGET_PLAYFAB_ID,
            playerIp = TARGET_PLAYER_IP,
            displayName = "Target Commerce Hub",
        ).copy(
            economy = com.hackwars.rewrite.gamecore.EconomyState(
                pettyCash = 250.0,
                bankMoney = 900.0,
                defaultBankPort = 1,
                defaultRedirectPort = 19,
            ),
            hardware = HardwareState(
                cpuType = 2,
                cpuMax = 90.0,
                memoryType = 2,
                hdType = 2,
                hdQuantity = targetFiles.size,
                hdMaximum = 120,
            ),
            ports = listOf(
                PortState(
                    number = 1,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    health = 97.0,
                    note = "Target bank",
                    maxCpuCost = 35.0,
                    installedApplication = installedApplication(
                        name = "target_bank.bin",
                        kind = ApplicationKind.BANKING,
                        maker = "High",
                        binaryPath = "/System/target_bank.bin",
                        cpuCost = 6.0,
                        banking = true,
                        scriptBundle = bankingScriptBundle(),
                    ),
                ),
                PortState(
                    number = 17,
                    type = "ftp",
                    enabled = true,
                    defaultPort = true,
                    health = 80.0,
                    note = "Public FTP",
                    maxCpuCost = 30.0,
                    installedApplication = installedApplication(
                        name = "target_ftp.bin",
                        kind = ApplicationKind.FTP,
                        maker = "Medium",
                        binaryPath = "/System/target_ftp.bin",
                        cpuCost = 4.0,
                        scriptBundle = ftpScriptBundle(),
                    ),
                    installedFirewall = installedFirewall(
                        name = "target_shield.bin",
                        maker = "Medium",
                        binaryPath = "/System/target_shield.bin",
                        cpuCost = 4.0,
                        strength = 20,
                    ),
                ),
                PortState(
                    number = 18,
                    type = "http",
                    enabled = true,
                    defaultPort = true,
                    health = 88.0,
                    note = "Daily pay target",
                    maxCpuCost = 32.0,
                    installedApplication = installedApplication(
                        name = "target_http.bin",
                        kind = ApplicationKind.HTTP,
                        maker = "Medium",
                        binaryPath = "/System/target_http.bin",
                        cpuCost = 7.0,
                        scriptBundle = httpScriptBundle(),
                    ),
                ),
                PortState(
                    number = 19,
                    type = "redirect",
                    enabled = true,
                    health = 84.0,
                    note = "Shipping lane",
                    maxCpuCost = 34.0,
                    installedApplication = installedApplication(
                        name = "target_redirect.bin",
                        kind = ApplicationKind.REDIRECT,
                        maker = "Medium",
                        binaryPath = "/System/target_redirect.bin",
                        cpuCost = 8.0,
                        scriptBundle = redirectScriptBundle(),
                    ),
                ),
                PortState(
                    number = 20,
                    type = "attack",
                    enabled = true,
                    health = 90.0,
                    note = "Counter attack",
                    maxCpuCost = 40.0,
                    installedApplication = installedApplication(
                        name = "target_attack.bin",
                        kind = ApplicationKind.ATTACK,
                        maker = "High",
                        binaryPath = "/System/target_attack.bin",
                        cpuCost = 9.0,
                        scriptBundle = attackScriptBundle(),
                    ),
                ),
            ),
            network = NetworkState(
                currentNetworkName = "ProgNet",
                storeStateId = storeStateId,
                allowedNetworks = setOf(ROOT_NETWORK_NAME),
            ),
            filesystem = filesystemState(
                currentPath = "/",
                files = targetFiles,
            ),
            website = WebsiteState(
                title = "Target Commerce Hub",
                body = """
                    <html>
                    <body>
                    <h1>Target Commerce Hub</h1>
                    <p>This target exposes FTP, HTTP, and shipping follow-up ports.</p>
                    </body>
                    </html>
                """.trimIndent(),
                voteCount = 12,
                votesAvailable = 0,
                storeRevenueTargetStateId = targetStateId,
            ),
            logs = LogState(
                entries = listOf(
                    ComputerLogEntry(
                        createdAtEpochMillis = clockMillis() - 7_500L,
                        renderedLine = "Incoming connections stabilized on $TARGET_PLAYER_IP",
                        sourceIp = TARGET_PLAYER_IP,
                    ),
                ),
            ),
            stats = PlayerStatsState(totalLevel = 15, noobProtectionLevel = 1),
            runtime = RuntimeState(currentCpuLoad = 8.0),
        )

        val zombieState = ComputerState.empty(
            id = zombieStateId,
            playFabId = ZOMBIE_PLAYFAB_ID,
            playerIp = ZOMBIE_PLAYER_IP,
            displayName = "Zombie Relay",
        ).copy(
            economy = com.hackwars.rewrite.gamecore.EconomyState(
                pettyCash = 80.0,
                bankMoney = 50.0,
            ),
            hardware = HardwareState(
                cpuType = 2,
                cpuMax = 80.0,
                memoryType = 2,
                hdType = 1,
                hdQuantity = 1,
                hdMaximum = 50,
            ),
            ports = listOf(
                PortState(
                    number = 5,
                    type = "attack",
                    enabled = true,
                    health = 94.0,
                    note = "Zombie attack source",
                    maxCpuCost = 45.0,
                    installedApplication = installedApplication(
                        name = "zombie_attack.bin",
                        kind = ApplicationKind.ATTACK,
                        maker = "Medium",
                        binaryPath = "/System/zombie_attack.bin",
                        cpuCost = 9.0,
                        scriptBundle = attackScriptBundle(),
                    ),
                ),
            ),
            filesystem = filesystemState(
                currentPath = "/",
                files = listOf(
                    textFile(
                        directoryPath = "/Public",
                        fileName = "zombie.txt",
                        contents = "Zombie relay ready for deterministic attack tests.",
                        maker = "ZombieOps",
                        kind = StoredFileKind.TEXT,
                    ),
                ),
            ),
            website = WebsiteState(
                title = "Zombie Relay",
                body = "<html><body><h1>Zombie Relay</h1></body></html>",
            ),
            runtime = RuntimeState(currentCpuLoad = 6.0),
        )

        val storeState = ComputerState.empty(
            id = storeStateId,
            playFabId = STORE_PLAYFAB_ID,
            playerIp = STORE_PLAYER_IP,
            displayName = "Shard Store",
            isNpc = true,
        ).copy(
            economy = com.hackwars.rewrite.gamecore.EconomyState(
                pettyCash = 10_000.0,
                bankMoney = 20_000.0,
                defaultBankPort = 1,
            ),
            hardware = HardwareState(
                cpuType = 4,
                cpuMax = 200.0,
                memoryType = 4,
                hdType = 3,
                hdQuantity = storeFiles.size,
                hdMaximum = 500,
            ),
            ports = listOf(
                PortState(
                    number = 1,
                    type = "banking",
                    enabled = true,
                    defaultPort = true,
                    note = "Store bank",
                    maxCpuCost = 40.0,
                    installedApplication = installedApplication(
                        name = "store_bank.bin",
                        kind = ApplicationKind.BANKING,
                        maker = "High",
                        binaryPath = "/System/store_bank.bin",
                        cpuCost = 6.0,
                        banking = true,
                        scriptBundle = bankingScriptBundle(),
                    ),
                ),
                PortState(
                    number = 17,
                    type = "ftp",
                    enabled = true,
                    defaultPort = true,
                    note = "Store FTP",
                    maxCpuCost = 32.0,
                    installedApplication = installedApplication(
                        name = "store_ftp.bin",
                        kind = ApplicationKind.FTP,
                        maker = "High",
                        binaryPath = "/System/store_ftp.bin",
                        cpuCost = 4.0,
                        scriptBundle = ftpScriptBundle(),
                    ),
                ),
                PortState(
                    number = 18,
                    type = "http",
                    enabled = true,
                    defaultPort = true,
                    note = "Store HTTP",
                    maxCpuCost = 36.0,
                    installedApplication = installedApplication(
                        name = "store_http.bin",
                        kind = ApplicationKind.HTTP,
                        maker = "High",
                        binaryPath = "/System/store_http.bin",
                        cpuCost = 7.0,
                        scriptBundle = httpScriptBundle(),
                    ),
                ),
            ),
            filesystem = filesystemState(
                currentPath = "/",
                files = storeFiles,
            ),
            website = WebsiteState(
                title = "Shard Store",
                body = """
                    <html>
                    <body>
                    <h1>Shard Store</h1>
                    <p>Deterministic market inventory for rewrite dev mode.</p>
                    </body>
                    </html>
                """.trimIndent(),
                voteCount = 99,
                votesAvailable = 0,
                storeRevenueTargetStateId = storeStateId,
            ),
            stats = PlayerStatsState(totalLevel = 50, noobProtectionLevel = 1),
            runtime = RuntimeState(currentCpuLoad = 4.0),
        )

        return linkedMapOf(
            localState.id to localState,
            targetState.id to targetState,
            zombieState.id to zombieState,
            storeState.id to storeState,
        )
    }

    private fun buildLocalFiles(): List<StoredFile> {
        val sourceFiles = listOf(
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "bank.src",
                maker = "Medium",
                compileCost = 45.0,
                cpuCost = 6.0,
                binaryMetadata = bankingBinaryMetadata("bank.bin"),
                scriptBundle = bankingScriptBundle(),
                description = "Deterministic banking source for rewrite dev mode.",
            ),
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "attack.src",
                maker = "Medium",
                compileCost = 55.0,
                cpuCost = 9.0,
                binaryMetadata = attackBinaryMetadata("attack.bin"),
                scriptBundle = attackScriptBundle(),
                description = "Deterministic attack source for rewrite dev mode.",
            ),
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "ftp.src",
                maker = "Medium",
                compileCost = 30.0,
                cpuCost = 4.0,
                binaryMetadata = ftpBinaryMetadata("ftp.bin"),
                scriptBundle = ftpScriptBundle(),
                description = "Deterministic FTP source for rewrite dev mode.",
            ),
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "http.src",
                maker = "High",
                compileCost = 60.0,
                cpuCost = 7.0,
                binaryMetadata = httpBinaryMetadata("http.bin"),
                scriptBundle = httpScriptBundle(),
                description = "Deterministic HTTP source for rewrite dev mode.",
            ),
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "redirect.src",
                maker = "Medium",
                compileCost = 50.0,
                cpuCost = 8.0,
                binaryMetadata = redirectBinaryMetadata("redirect.bin"),
                scriptBundle = redirectScriptBundle(),
                description = "Deterministic redirect source for rewrite dev mode.",
            ),
            scriptSourceFile(
                directoryPath = "/Software/Sources",
                fileName = "watch.src",
                maker = "Low",
                compileCost = 20.0,
                cpuCost = 3.0,
                binaryMetadata = watchBinaryMetadata("watch.bin"),
                scriptBundle = watchScriptBundle(),
                description = "Deterministic watch source for rewrite dev mode.",
            ),
        )

        val binaryFiles = listOf(
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "bank.bin",
                maker = "Medium",
                cpuCost = 6.0,
                quantity = 3,
                metadata = bankingBinaryMetadata("bank.bin"),
                scriptBundle = bankingScriptBundle(),
            ),
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "attack.bin",
                maker = "Medium",
                cpuCost = 9.0,
                quantity = 2,
                metadata = attackBinaryMetadata("attack.bin"),
                scriptBundle = attackScriptBundle(),
            ),
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "ftp.bin",
                maker = "Medium",
                cpuCost = 4.0,
                quantity = 2,
                metadata = ftpBinaryMetadata("ftp.bin"),
                scriptBundle = ftpScriptBundle(),
            ),
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "http.bin",
                maker = "High",
                cpuCost = 7.0,
                quantity = 2,
                metadata = httpBinaryMetadata("http.bin"),
                scriptBundle = httpScriptBundle(),
            ),
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "redirect.bin",
                maker = "Medium",
                cpuCost = 8.0,
                quantity = 2,
                metadata = redirectBinaryMetadata("redirect.bin"),
                scriptBundle = redirectScriptBundle(),
            ),
            applicationBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "watch.bin",
                maker = "Low",
                cpuCost = 3.0,
                quantity = 3,
                metadata = watchBinaryMetadata("watch.bin"),
                scriptBundle = watchScriptBundle(),
            ),
            firewallBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "shield.bin",
                maker = "Medium",
                cpuCost = 4.0,
                quantity = 2,
                metadata = firewallBinaryMetadata("shield.bin", strength = 30),
            ),
            firewallBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "counterwall.bin",
                maker = "High",
                cpuCost = 6.0,
                quantity = 1,
                metadata = firewallBinaryMetadata("counterwall.bin", strength = 45, attackBackDamage = 2.0),
            ),
            equipmentBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "cpu_boost.bin",
                maker = "Medium",
                cpuCost = 12.0,
                quantity = 1,
                metadata = equipmentBinaryMetadata(
                    outputName = "cpu_boost.bin",
                    slot = EquipmentSlot.CPU,
                    healCostMultiplier = 0.8,
                    healModifierDelta = -1,
                ),
            ),
            equipmentBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "memory_boost.bin",
                maker = "Low",
                cpuCost = 0.0,
                quantity = 1,
                metadata = equipmentBinaryMetadata("memory_boost.bin", EquipmentSlot.MEMORY),
            ),
            equipmentBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "storage_boost.bin",
                maker = "Low",
                cpuCost = 0.0,
                quantity = 1,
                metadata = equipmentBinaryMetadata("storage_boost.bin", EquipmentSlot.STORAGE),
            ),
            equipmentBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "pci_watch.bin",
                maker = "Low",
                cpuCost = 0.0,
                quantity = 1,
                metadata = equipmentBinaryMetadata("pci_watch.bin", EquipmentSlot.PCI),
            ),
            equipmentBinaryFile(
                directoryPath = "/Software/Binaries",
                fileName = "agp_guard.bin",
                maker = "Low",
                cpuCost = 0.0,
                quantity = 1,
                metadata = equipmentBinaryMetadata("agp_guard.bin", EquipmentSlot.AGP),
            ),
        )

        return sourceFiles +
            binaryFiles +
            listOf(
                applicationBinaryFile(
                    directoryPath = "/Store",
                    fileName = "listed_attack.bin",
                    maker = "Medium",
                    cpuCost = 9.0,
                    quantity = 1,
                    price = 175.0,
                    metadata = attackBinaryMetadata("listed_attack.bin"),
                    scriptBundle = attackScriptBundle(),
                ),
                firewallBinaryFile(
                    directoryPath = "/Store",
                    fileName = "listed_shield.bin",
                    maker = "Medium",
                    cpuCost = 4.0,
                    quantity = 1,
                    price = 120.0,
                    metadata = firewallBinaryMetadata("listed_shield.bin", strength = 30),
                ),
                textFile(
                    directoryPath = "/Notes",
                    fileName = "readme.txt",
                    contents = "Rewrite dev mode is wired to a deterministic in-memory GAME harness.",
                    maker = "Alexander",
                    kind = StoredFileKind.TEXT,
                ),
                textFile(
                    directoryPath = "/Notes",
                    fileName = "ops.note",
                    contents = "Targets to try: 198.51.100.20 for scan/attack/public FTP and 203.0.113.30 for zombie runs.",
                    maker = "Alexander",
                    kind = StoredFileKind.NOTE,
                ),
                textFile(
                    directoryPath = "/Public",
                    fileName = "hello.txt",
                    contents = "Public data from the deterministic local host.",
                    maker = "Alexander",
                    kind = StoredFileKind.TEXT,
                ),
            )
    }

    private fun buildTargetFiles(): List<StoredFile> {
        return listOf(
            textFile(
                directoryPath = "/Public",
                fileName = "loot.txt",
                contents = "Target public FTP listing for rewrite dev mode.",
                maker = "TargetOps",
                kind = StoredFileKind.TEXT,
            ),
            applicationBinaryFile(
                directoryPath = "/Store",
                fileName = "shipping_manifest.bin",
                maker = "TargetOps",
                cpuCost = 8.0,
                quantity = 2,
                price = 220.0,
                metadata = redirectBinaryMetadata("shipping_manifest.bin"),
                scriptBundle = redirectScriptBundle(),
            ),
        )
    }

    private fun buildStoreFiles(): List<StoredFile> {
        return listOf(
            applicationBinaryFile(
                directoryPath = "/Store",
                fileName = "market_bank.bin",
                maker = "High",
                cpuCost = 6.0,
                quantity = 2,
                price = 220.0,
                metadata = bankingBinaryMetadata("market_bank.bin"),
                scriptBundle = bankingScriptBundle(),
            ),
            firewallBinaryFile(
                directoryPath = "/Store",
                fileName = "market_shield.bin",
                maker = "Medium",
                cpuCost = 4.0,
                quantity = 2,
                price = 150.0,
                metadata = firewallBinaryMetadata("market_shield.bin", strength = 35),
            ),
            equipmentBinaryFile(
                directoryPath = "/Store",
                fileName = "market_cpu.bin",
                maker = "Medium",
                cpuCost = 12.0,
                quantity = 1,
                price = 350.0,
                metadata = equipmentBinaryMetadata(
                    outputName = "market_cpu.bin",
                    slot = EquipmentSlot.CPU,
                    healCostMultiplier = 0.8,
                    healModifierDelta = -1,
                ),
            ),
        )
    }
}

private object EmptySearchCatalogRepository : SearchCatalogRepository {
    override suspend fun loadDocuments(): List<SearchableWebsiteDocument> = emptyList()
}

private class HarnessBackedRewriteServiceSessionGateway(
    private val harness: InMemoryRewriteServiceHarness,
    private val runtimeScope: CoroutineScope,
) : RewriteServiceSessionGateway, AutoCloseable {
    private val lock = Any()
    private val sessions = linkedSetOf<HarnessBackedRewriteServiceSession>()

    override fun open(
        service: RewriteService,
        onInboundFrame: (FrameEnvelope) -> Unit,
    ): RewriteServiceSession {
        require(service == RewriteService.GAME) {
            "RewriteClientDev only supports GAME sessions in this slice."
        }
        val session = HarnessBackedRewriteServiceSession(
            service = service,
            connection = harness.connect("rewrite-client-dev-game"),
            runtimeScope = runtimeScope,
            onInboundFrame = onInboundFrame,
            onClosed = { closedSession ->
                synchronized(lock) {
                    sessions.remove(closedSession)
                }
            },
        )
        synchronized(lock) {
            sessions += session
        }
        return session
    }

    override fun close() {
        val activeSessions = synchronized(lock) {
            val snapshot = sessions.toList()
            sessions.clear()
            snapshot
        }
        activeSessions.forEach { it.close() }
    }
}

private class HarnessBackedRewriteServiceSession(
    override val service: RewriteService,
    private val connection: InMemoryClientConnection,
    runtimeScope: CoroutineScope,
    private val onInboundFrame: (FrameEnvelope) -> Unit,
    private val onClosed: (HarnessBackedRewriteServiceSession) -> Unit,
) : RewriteServiceSession {
    private val closed = AtomicBoolean(false)
    private val readerJob: Job = runtimeScope.launch {
        while (!closed.get()) {
            val nextFrame = connection.receiveNextFrame() ?: break
            onInboundFrame(nextFrame)
        }
    }

    override suspend fun send(frame: FrameEnvelope) {
        connection.send(frame)
    }

    override fun receive(frame: FrameEnvelope) {
        onInboundFrame(frame)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        connection.close()
        readerJob.cancel()
        onClosed(this)
    }
}

private class HarnessBackedGameAdapter(
    private val adapter: RewriteGameProtocolAdapter,
) : RewriteServiceAdapter {
    override val service: RewriteService = RewriteService.GAME

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
        command: CommandEnvelope,
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

private fun InMemoryAuthenticatedSession.toGameSession(): AuthenticatedGameSession {
    return AuthenticatedGameSession(
        connectionId = connectionId,
        playFabId = verifiedSession.playFabId,
        playerIp = verifiedSession.playerIp,
    )
}

private fun filesystemState(
    currentPath: String,
    files: List<StoredFile>,
): FilesystemState {
    var filesystem = FilesystemState(currentPath = currentPath)
    files.forEach { file ->
        filesystem = filesystem.ensureDirectory(parentDirectoryOf(file.path))
    }
    return filesystem.copy(
        filesByPath = linkedMapOf<String, StoredFile>().apply {
            files.forEach { file ->
                put(file.path, file)
            }
        },
    )
}

private fun localPorts(): List<PortState> {
    return listOf(
        PortState(
            number = 1,
            type = "banking",
            enabled = true,
            defaultPort = true,
            health = 98.0,
            healCount = 4,
            note = "Primary bank",
            maxCpuCost = 40.0,
            installedApplication = installedApplication(
                name = "bank.bin",
                kind = ApplicationKind.BANKING,
                maker = "Medium",
                binaryPath = buildFilePath("/Software/Binaries", "bank.bin"),
                cpuCost = 6.0,
                banking = true,
                scriptBundle = bankingScriptBundle(),
            ),
        ),
        PortState(
            number = 5,
            type = "attack",
            enabled = true,
            health = 92.0,
            healCount = 3,
            note = "Attack runner",
            maxCpuCost = 55.0,
            installedApplication = installedApplication(
                name = "attack.bin",
                kind = ApplicationKind.ATTACK,
                maker = "Medium",
                binaryPath = buildFilePath("/Software/Binaries", "attack.bin"),
                cpuCost = 9.0,
                scriptBundle = attackScriptBundle(),
            ),
            installedFirewall = installedFirewall(
                name = "shield.bin",
                maker = "Medium",
                binaryPath = buildFilePath("/Software/Binaries", "shield.bin"),
                cpuCost = 4.0,
                strength = 30,
            ),
        ),
        PortState(
            number = 6,
            type = "redirect",
            enabled = true,
            defaultPort = true,
            health = 90.0,
            healCount = 2,
            note = "Redirect lane",
            maxCpuCost = 45.0,
            installedApplication = installedApplication(
                name = "redirect.bin",
                kind = ApplicationKind.REDIRECT,
                maker = "Medium",
                binaryPath = buildFilePath("/Software/Binaries", "redirect.bin"),
                cpuCost = 8.0,
                scriptBundle = redirectScriptBundle(),
            ),
        ),
        PortState(
            number = 7,
            type = "ftp",
            enabled = true,
            defaultPort = true,
            health = 100.0,
            healCount = 5,
            note = "FTP service",
            maxCpuCost = 35.0,
            installedApplication = installedApplication(
                name = "ftp.bin",
                kind = ApplicationKind.FTP,
                maker = "Medium",
                binaryPath = buildFilePath("/Software/Binaries", "ftp.bin"),
                cpuCost = 4.0,
                scriptBundle = ftpScriptBundle(),
            ),
        ),
        PortState(
            number = 8,
            type = "http",
            enabled = true,
            defaultPort = true,
            health = 96.0,
            healCount = 1,
            note = "HTTP service",
            maxCpuCost = 38.0,
            installedApplication = installedApplication(
                name = "http.bin",
                kind = ApplicationKind.HTTP,
                maker = "High",
                binaryPath = buildFilePath("/Software/Binaries", "http.bin"),
                cpuCost = 7.0,
                scriptBundle = httpScriptBundle(),
            ),
            installedFirewall = installedFirewall(
                name = "counterwall.bin",
                maker = "High",
                binaryPath = buildFilePath("/Software/Binaries", "counterwall.bin"),
                cpuCost = 6.0,
                strength = 45,
                attackBackDamage = 2.0,
            ),
        ),
        PortState(
            number = 13,
            type = "dummy",
            enabled = false,
            dummy = true,
            health = 100.0,
            healCount = 0,
            note = "Read-only dummy port",
            maxCpuCost = 0.0,
        ),
    )
}

private fun textFile(
    directoryPath: String,
    fileName: String,
    contents: String,
    maker: String,
    kind: StoredFileKind,
): StoredFile {
    return StoredFile(
        path = buildFilePath(directoryPath, fileName),
        name = fileName,
        kind = kind,
        contents = contents,
        maker = maker,
    )
}

private fun scriptSourceFile(
    directoryPath: String,
    fileName: String,
    maker: String,
    compileCost: Double,
    cpuCost: Double,
    binaryMetadata: CompiledBinaryMetadata,
    scriptBundle: ProgramScriptBundle,
    description: String,
): StoredFile {
    return StoredFile(
        path = buildFilePath(directoryPath, fileName),
        name = fileName,
        kind = StoredFileKind.SCRIPT_SOURCE,
        contents = renderScriptContents(scriptBundle),
        description = description,
        maker = maker,
        compileCost = compileCost,
        cpuCost = cpuCost,
        compiledBinary = binaryMetadata,
        scriptBundle = scriptBundle,
    )
}

private fun applicationBinaryFile(
    directoryPath: String,
    fileName: String,
    maker: String,
    cpuCost: Double,
    quantity: Int,
    metadata: CompiledBinaryMetadata,
    scriptBundle: ProgramScriptBundle,
    price: Double = 0.0,
): StoredFile {
    return StoredFile(
        path = buildFilePath(directoryPath, fileName),
        name = fileName,
        kind = StoredFileKind.APPLICATION_BINARY,
        contents = renderScriptContents(scriptBundle),
        quantity = quantity,
        maker = maker,
        compileCost = 25.0,
        cpuCost = cpuCost,
        price = price,
        compiledBinary = metadata,
        scriptBundle = scriptBundle,
    )
}

private fun firewallBinaryFile(
    directoryPath: String,
    fileName: String,
    maker: String,
    cpuCost: Double,
    quantity: Int,
    metadata: CompiledBinaryMetadata,
    price: Double = 0.0,
): StoredFile {
    return StoredFile(
        path = buildFilePath(directoryPath, fileName),
        name = fileName,
        kind = StoredFileKind.FIREWALL_BINARY,
        contents = "compiled firewall",
        quantity = quantity,
        maker = maker,
        compileCost = 35.0,
        cpuCost = cpuCost,
        price = price,
        compiledBinary = metadata,
    )
}

private fun equipmentBinaryFile(
    directoryPath: String,
    fileName: String,
    maker: String,
    cpuCost: Double,
    quantity: Int,
    metadata: CompiledBinaryMetadata,
    price: Double = 0.0,
): StoredFile {
    return StoredFile(
        path = buildFilePath(directoryPath, fileName),
        name = fileName,
        kind = StoredFileKind.EQUIPMENT_BINARY,
        contents = "compiled equipment",
        quantity = quantity,
        maker = maker,
        compileCost = 40.0,
        cpuCost = cpuCost,
        price = price,
        compiledBinary = metadata,
    )
}

private fun installedApplication(
    name: String,
    kind: ApplicationKind,
    maker: String,
    binaryPath: String,
    cpuCost: Double,
    banking: Boolean = false,
    scriptBundle: ProgramScriptBundle? = null,
): InstalledApplication {
    return InstalledApplication(
        name = name,
        kind = kind,
        maker = maker,
        binaryPath = binaryPath,
        cpuCost = cpuCost,
        banking = banking || kind == ApplicationKind.BANKING,
        scriptBundle = scriptBundle,
        maliciousConfig = MaliciousProgramConfig(targetIp = TARGET_PLAYER_IP, pettyCashTarget = 25.0),
    )
}

private fun installedFirewall(
    name: String,
    maker: String,
    binaryPath: String,
    cpuCost: Double,
    strength: Int,
    attackBackDamage: Double = 0.0,
): InstalledFirewall {
    return InstalledFirewall(
        name = name,
        kind = FirewallKind.BASIC,
        maker = maker,
        binaryPath = binaryPath,
        strength = strength,
        cpuCost = cpuCost,
        combatProfile = FirewallCombatProfile(attackBackDamage = attackBackDamage),
        actionProfile = FirewallActionProfile(),
    )
}

private fun bankingBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.BANKING,
        outputName = outputName,
        applicationKind = ApplicationKind.BANKING,
        bankingApplication = true,
        experienceAward = 25.0,
    )
}

private fun attackBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.ATTACK,
        outputName = outputName,
        applicationKind = ApplicationKind.ATTACK,
        experienceAward = 30.0,
    )
}

private fun ftpBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.GENERAL,
        outputName = outputName,
        applicationKind = ApplicationKind.FTP,
        experienceAward = 15.0,
    )
}

private fun httpBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.HTTP,
        outputName = outputName,
        applicationKind = ApplicationKind.HTTP,
        experienceAward = 20.0,
    )
}

private fun redirectBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.REDIRECT,
        outputName = outputName,
        applicationKind = ApplicationKind.REDIRECT,
        experienceAward = 20.0,
    )
}

private fun watchBinaryMetadata(outputName: String): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.WATCH,
        outputName = outputName,
        applicationKind = ApplicationKind.WATCH,
        experienceAward = 12.0,
    )
}

private fun firewallBinaryMetadata(
    outputName: String,
    strength: Int,
    attackBackDamage: Double = 0.0,
): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.FIREWALL,
        outputName = outputName,
        firewallKind = FirewallKind.BASIC,
        firewallCombatProfile = FirewallCombatProfile(attackBackDamage = attackBackDamage),
        firewallActionProfile = FirewallActionProfile(),
        strength = strength,
        experienceAward = 10.0,
    )
}

private fun equipmentBinaryMetadata(
    outputName: String,
    slot: EquipmentSlot,
    healCostMultiplier: Double? = null,
    healModifierDelta: Int? = null,
): CompiledBinaryMetadata {
    return CompiledBinaryMetadata(
        scriptFamily = ScriptFamily.GENERAL,
        outputName = outputName,
        equipmentSlot = slot,
        healCostMultiplier = healCostMultiplier,
        healModifierDelta = healModifierDelta,
        experienceAward = 8.0,
    )
}

private fun bankingScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.BANKING,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.DEPOSIT to basicMainScript(),
            ProgramScriptSlot.WITHDRAW to basicMainScript(),
            ProgramScriptSlot.TRANSFER to basicMainScript(),
        ),
    )
}

private fun attackScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.ATTACK,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.INITIALIZE to basicMainScript(),
            ProgramScriptSlot.CONTINUE to showChoicesScript(),
            ProgramScriptSlot.FINALIZE to basicMainScript(),
        ),
    )
}

private fun ftpScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.GENERAL,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.PUT to basicMainScript(),
            ProgramScriptSlot.GET to basicMainScript(),
        ),
    )
}

private fun httpScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.HTTP,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.ENTER to basicMainScript(),
            ProgramScriptSlot.EXIT to basicMainScript(),
            ProgramScriptSlot.SUBMIT to basicMainScript(),
        ),
    )
}

private fun redirectScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.REDIRECT,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.INITIALIZE to basicMainScript(),
            ProgramScriptSlot.CONTINUE to showChoicesScript(),
            ProgramScriptSlot.FINALIZE to basicMainScript(),
        ),
    )
}

private fun watchScriptBundle(): ProgramScriptBundle {
    return ProgramScriptBundle(
        family = ScriptFamily.WATCH,
        scriptsBySlot = linkedMapOf(
            ProgramScriptSlot.FIRE to basicMainScript(),
        ),
    )
}

private fun basicMainScript(): String {
    return """
        int main() {
            return 0;
        }
    """.trimIndent()
}

private fun showChoicesScript(): String {
    return """
        int main() {
            showChoices();
            cancelAttack();
            return 0;
        }
    """.trimIndent()
}

private fun renderScriptContents(scriptBundle: ProgramScriptBundle): String {
    return scriptBundle.scriptsBySlot.entries
        .sortedBy { it.key.ordinal }
        .joinToString(separator = "\n\n") { (slot, script) ->
            "// ${slot.name}\n$script"
        }
}
