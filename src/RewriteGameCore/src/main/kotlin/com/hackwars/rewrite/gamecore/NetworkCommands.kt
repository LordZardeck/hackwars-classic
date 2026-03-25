package com.hackwars.rewrite.gamecore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangeNetworkPayload(
    val ip: String,
    val network: String? = null,
)

@Serializable
data class RequestScanPayload(
    val ip: String,
    @SerialName("targetIP")
    val targetIp: String? = null,
)

class GrantNetworkAccessCommand(
    private val stateId: GameStateId,
    private val networkName: String,
) : RequestCommand<MutationAcceptedResponse> {
    override val name: String = "giveaccess"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): MutationAcceptedResponse {
        val trimmedNetworkName = networkName.trim()
        require(trimmedNetworkName.isNotEmpty()) { "A network name is required." }

        val state = context.requireExistingState(stateId)
        if (state.network.allowedNetworks.contains(trimmedNetworkName)) {
            return MutationAcceptedResponse(
                stateId = stateId,
                version = state.version,
                message = "network-access-unchanged",
            )
        }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                NetworkStateChangedEvent(
                    network = state.network.copy(
                        allowedNetworks = state.network.allowedNetworks + trimmedNetworkName,
                    ),
                ),
            ),
        )
        return MutationAcceptedResponse(
            stateId = stateId,
            version = updated.version,
            message = "network-access-granted",
        )
    }
}

class RefreshCurrentNetworkDirectoryCommand(
    private val stateId: GameStateId,
    private val networkDirectoryRepository: NetworkDirectoryRepository,
) : RequestCommand<NetworkDirectoryRefreshResult> {
    override val name: String = "refreshcurrentnetworkdirectory"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): NetworkDirectoryRefreshResult {
        val state = context.requireExistingState(stateId)
        val refreshedNetwork = resolveNetworkDirectoryState(
            state = state,
            networkDirectoryRepository = networkDirectoryRepository,
        )
        if (refreshedNetwork == state.network) {
            return NetworkDirectoryRefreshResult(
                stateId = stateId,
                changed = false,
                network = state.network,
                version = state.version,
            )
        }

        val updated = context.appendEvents(
            id = stateId,
            events = listOf(NetworkStateChangedEvent(refreshedNetwork)),
        )
        return NetworkDirectoryRefreshResult(
            stateId = stateId,
            changed = true,
            network = updated.network,
            version = updated.version,
        )
    }
}

class ChangeNetworkCommand(
    private val stateId: GameStateId,
    private val targetNetworkName: String?,
    private val networkDirectoryRepository: NetworkDirectoryRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<NetworkSwitchResponse> {
    override val name: String = "changenetwork"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): NetworkSwitchResponse {
        val state = context.requireExistingState(stateId)
        return switchNetwork(
            context = context,
            state = state,
            bypassRestrictions = false,
        )
    }

    internal suspend fun switchNetwork(
        context: CommandContext,
        state: ComputerState,
        bypassRestrictions: Boolean,
    ): NetworkSwitchResponse {
        val requestedNetwork = targetNetworkName.orEmpty().trim()
        if (requestedNetwork.isEmpty()) {
            return failureResponse(
                state = state,
                requestedNetwork = requestedNetwork,
                code = NetworkSwitchFailureCode.INVALID_TARGET,
                message = "A target network is required.",
            )
        }
        if (state.network.currentNetworkName == requestedNetwork) {
            return failureResponse(
                state = state,
                requestedNetwork = requestedNetwork,
                code = NetworkSwitchFailureCode.ALREADY_ON_NETWORK,
                message = "You are already on ${state.network.currentNetworkName}.",
            )
        }
        if (!bypassRestrictions && state.network.currentNetworkName == JAIL_NETWORK_NAME) {
            return failureResponse(
                state = state,
                requestedNetwork = requestedNetwork,
                code = NetworkSwitchFailureCode.JAILED,
                message = "You cannot switch networks while on ${state.network.currentNetworkName}.",
            )
        }
        val now = clock()
        if (!bypassRestrictions && now - state.network.lastNetworkSwitchAtEpochMillis < NETWORK_SWITCH_COOLDOWN_MS) {
            return failureResponse(
                state = state,
                requestedNetwork = requestedNetwork,
                code = NetworkSwitchFailureCode.COOLDOWN,
                message = "You must wait before changing networks from ${state.network.currentNetworkName}.",
            )
        }

        val targetDefinition = networkDirectoryRepository.loadNetwork(requestedNetwork)
            ?: return failureResponse(
                state = state,
                requestedNetwork = requestedNetwork,
                code = NetworkSwitchFailureCode.UNKNOWN_NETWORK,
                message = "Network $requestedNetwork does not exist.",
            )

        if (!bypassRestrictions && requestedNetwork != ROOT_NETWORK_NAME) {
            val validation = networkDirectoryRepository.validateSwitch(
                fromNetwork = state.network.currentNetworkName,
                toNetwork = requestedNetwork,
                allowedNetworks = state.network.allowedNetworks,
            )
            if (!validation.allowed) {
                return failureResponse(
                    state = state,
                    requestedNetwork = requestedNetwork,
                    code = NetworkSwitchFailureCode.DISALLOWED,
                    message = validation.failureMessage,
                )
            }
        }

        val updated = context.appendEvents(
            id = state.id,
            events = listOf(
                NetworkStateChangedEvent(
                    network = state.network.copy(
                        currentNetworkName = targetDefinition.name,
                        storeStateId = targetDefinition.storeStateId,
                        lastNetworkSwitchAtEpochMillis = now,
                        regularNpcs = targetDefinition.regularNpcs,
                        questNpcs = targetDefinition.questNpcs,
                        miningNpcs = targetDefinition.miningNpcs,
                        storeNpcs = targetDefinition.storeNpcs,
                    ),
                ),
            ),
        )

        return NetworkSwitchResponse(
            stateId = updated.id,
            requestedNetworkName = requestedNetwork,
            currentNetworkName = updated.network.currentNetworkName,
            storeStateId = updated.network.storeStateId,
            accepted = true,
            failureCode = null,
            message = "Changed network to ${updated.network.currentNetworkName}.",
            version = updated.version,
        )
    }

    private fun failureResponse(
        state: ComputerState,
        requestedNetwork: String,
        code: NetworkSwitchFailureCode,
        message: String,
    ): NetworkSwitchResponse {
        return NetworkSwitchResponse(
            stateId = state.id,
            requestedNetworkName = requestedNetwork,
            currentNetworkName = state.network.currentNetworkName,
            storeStateId = state.network.storeStateId,
            accepted = false,
            failureCode = code,
            message = message,
            version = state.version,
        )
    }
}

class ChangeNetworkDirectCommand(
    private val stateId: GameStateId,
    private val targetNetworkName: String?,
    private val networkDirectoryRepository: NetworkDirectoryRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<NetworkSwitchResponse> {
    override val name: String = "changenetwork2"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): NetworkSwitchResponse {
        return ChangeNetworkCommand(
            stateId = stateId,
            targetNetworkName = targetNetworkName,
            networkDirectoryRepository = networkDirectoryRepository,
            clock = clock,
        ).let { publicCommand ->
            val state = context.requireExistingState(stateId)
            publicCommand.run {
                switchNetwork(context = context, state = state, bypassRestrictions = true)
            }
        }
    }
}

class RequestScanCommand(
    private val requesterStateId: GameStateId,
    private val targetStateId: GameStateId,
) : RequestCommand<ScanResponse> {
    override val name: String = "requestscan"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ScanResponse {
        val states = context.loadStates(targetStateIds)
        val requesterState = requireNotNull(states[requesterStateId]) {
            "No requester state exists for ${requesterStateId.value}."
        }
        if (targetStateId == requesterStateId) {
            return failureResponse(
                requesterState = requesterState,
                code = ScanFailureCode.SELF_TARGET,
                message = "You cannot scan your own state.",
            )
        }
        val targetState = states[targetStateId] ?: return failureResponse(
            requesterState = requesterState,
            code = ScanFailureCode.TARGET_NOT_FOUND,
            message = "Target ${targetStateId.value} does not exist.",
        )
        if (!requesterState.hasActiveDefaultBankPort()) {
            return failureResponse(
                requesterState = requesterState,
                code = ScanFailureCode.ACTIVE_BANK_REQUIRED,
                message = "Scanning requires an active default banking port.",
            )
        }
        if (requesterState.isOverheated()) {
            return failureResponse(
                requesterState = requesterState,
                code = ScanFailureCode.OVERHEATED,
                message = "Scanning failed because the computer is overheated.",
            )
        }
        if (requesterState.economy.pettyCash < SCAN_COST) {
            return failureResponse(
                requesterState = requesterState,
                code = ScanFailureCode.INSUFFICIENT_PETTY_CASH,
                message = "Scanning requires at least \$10 petty cash.",
            )
        }

        val scanLevel = legacyLevelForXp(requesterState.stats.skillExperience(ScriptFamily.SCANNING))
        val firewallLevel = legacyLevelForXp(targetState.stats.skillExperience(ScriptFamily.FIREWALL))
        val revealDelta = scanLevel - firewallLevel
        val experienceAward = when {
            revealDelta < 15 -> 20.0
            revealDelta < 25 -> 40.0
            else -> 60.0
        }

        val updatedRequester = context.appendEvents(
            id = requesterStateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = -SCAN_COST),
                SkillExperienceAdjustedEvent(family = ScriptFamily.SCANNING, delta = experienceAward),
            ),
        )
        context.evaluatePassivePettyCashChange(
            targetStateId = requesterStateId,
            previousPettyCash = requesterState.economy.pettyCash,
            newPettyCash = updatedRequester.economy.pettyCash,
        )
        context.evaluatePassiveScanSuccess(
            targetStateId = requesterStateId,
            sourceIp = targetStateId.value,
            external = true,
        )

        return ScanResponse(
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            accepted = true,
            failureCode = null,
            failureMessage = null,
            chargedAmount = SCAN_COST,
            experienceAwarded = experienceAward,
            pettyCashAfter = updatedRequester.economy.pettyCash,
            scanningExperienceAfter = updatedRequester.stats.skillExperience(ScriptFamily.SCANNING),
            ports = targetState.ports
                .filter { it.enabled }
                .sortedBy { it.number }
                .map { port ->
                    port.toScannedPortView(
                        sourceIp = requesterStateId.value,
                        revealDefaults = revealDelta >= 25,
                        revealFirewalls = revealDelta >= 15,
                    )
                },
            requesterVersion = updatedRequester.version,
        )
    }

    private fun failureResponse(
        requesterState: ComputerState,
        code: ScanFailureCode,
        message: String,
    ): ScanResponse {
        return ScanResponse(
            requesterStateId = requesterStateId,
            targetStateId = targetStateId,
            accepted = false,
            failureCode = code,
            failureMessage = message,
            requesterVersion = requesterState.version,
        )
    }
}

class InMemoryNetworkDirectoryRepository(
    private val definitions: Map<String, NetworkDirectoryDefinition>,
) : NetworkDirectoryRepository {
    override suspend fun loadNetwork(name: String): NetworkDirectoryDefinition? = definitions[name]

    override suspend fun validateSwitch(
        fromNetwork: String,
        toNetwork: String,
        allowedNetworks: Set<String>,
    ): NetworkSwitchValidation {
        if (toNetwork == ROOT_NETWORK_NAME || allowedNetworks.contains(toNetwork)) {
            return NetworkSwitchValidation(
                allowed = true,
                failureMessage = "",
            )
        }
        val failureMessage = definitions[fromNetwork]
            ?.switchMessagesByTarget
            ?.get(toNetwork)
            ?: "There is no connection between $fromNetwork and $toNetwork."
        return NetworkSwitchValidation(
            allowed = false,
            failureMessage = failureMessage,
        )
    }

    companion object {
        fun defaultWorld(serverId: String = "1"): InMemoryNetworkDirectoryRepository {
            val storeStateId = GameStateId("store$serverId")
            val root = NetworkDirectoryDefinition(
                name = ROOT_NETWORK_NAME,
                storeStateId = storeStateId,
                regularNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("ATTACK-NPC-1"),
                        displayName = "Root Attacker",
                        title = "Attack NPC",
                        category = NpcCategory.REGULAR,
                    ),
                ),
                questNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("QUEST-NPC-1"),
                        displayName = "Quest Guide",
                        title = "Quest NPC",
                        category = NpcCategory.QUEST,
                    ),
                ),
                miningNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("MINE-NPC-1"),
                        displayName = "Miner One",
                        title = "Mining NPC",
                        category = NpcCategory.MINING,
                        commodity = "Silicon",
                    ),
                ),
                storeNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = storeStateId,
                        displayName = "Shard Store",
                        title = "Store NPC",
                        category = NpcCategory.STORE,
                    ),
                ),
                switchMessagesByTarget = mapOf(
                    "ProgNet" to "A gateway to ProgNet is currently locked.",
                ),
            )
            val progNet = NetworkDirectoryDefinition(
                name = "ProgNet",
                storeStateId = storeStateId,
                regularNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("PROG-ATTACK-1"),
                        displayName = "Prog Runner",
                        title = "Attack NPC",
                        category = NpcCategory.REGULAR,
                    ),
                ),
                questNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("PROG-QUEST-1"),
                        displayName = "Prog Mentor",
                        title = "Quest NPC",
                        category = NpcCategory.QUEST,
                    ),
                ),
                miningNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = GameStateId("PROG-MINE-1"),
                        displayName = "Prog Miner",
                        title = "Mining NPC",
                        category = NpcCategory.MINING,
                        commodity = "Germanium",
                    ),
                ),
                storeNpcs = listOf(
                    NpcDirectoryEntry(
                        stateId = storeStateId,
                        displayName = "Shard Store",
                        title = "Store NPC",
                        category = NpcCategory.STORE,
                    ),
                ),
                switchMessagesByTarget = mapOf(
                    ROOT_NETWORK_NAME to "The uplink back to UGOPNet is unstable.",
                ),
            )
            val jail = NetworkDirectoryDefinition(
                name = JAIL_NETWORK_NAME,
                storeStateId = null,
            )
            return InMemoryNetworkDirectoryRepository(
                definitions = linkedMapOf(
                    ROOT_NETWORK_NAME to root,
                    "ProgNet" to progNet,
                    JAIL_NETWORK_NAME to jail,
                ),
            )
        }
    }
}

private fun PortState.toScannedPortView(
    sourceIp: String,
    revealDefaults: Boolean,
    revealFirewalls: Boolean,
): ScannedPortView {
    val effectiveCpuCost = (installedApplication?.cpuCost ?: 0.0) + (installedFirewall?.cpuCost ?: 0.0)
    return ScannedPortView(
        number = number,
        type = type,
        enabled = enabled,
        dummy = dummy,
        attacking = attacking,
        cpuCost = effectiveCpuCost,
        maxCpuCost = maxCpuCost.takeIf { it > 0.0 } ?: effectiveCpuCost,
        health = health,
        note = if (revealDefaults) sourceIp else "",
        defaultVisibility = when {
            !revealDefaults -> DefaultPortVisibility.UNKNOWN
            defaultPort -> DefaultPortVisibility.YES
            else -> DefaultPortVisibility.NO
        },
        firewall = installedFirewall?.takeIf { revealFirewalls }?.toFirewallView(),
    )
}

internal suspend fun resolveNetworkDirectoryState(
    state: ComputerState,
    networkDirectoryRepository: NetworkDirectoryRepository,
): NetworkState {
    val resolvedDefinition = networkDirectoryRepository.loadNetwork(state.network.currentNetworkName)
        ?: networkDirectoryRepository.loadNetwork(ROOT_NETWORK_NAME)
        ?: NetworkDirectoryDefinition(name = ROOT_NETWORK_NAME)

    return state.network.copy(
        currentNetworkName = resolvedDefinition.name,
        storeStateId = resolvedDefinition.storeStateId,
        regularNpcs = resolvedDefinition.regularNpcs,
        questNpcs = resolvedDefinition.questNpcs,
        miningNpcs = resolvedDefinition.miningNpcs,
        storeNpcs = resolvedDefinition.storeNpcs,
    )
}

private fun InstalledFirewall.toFirewallView(): FirewallView {
    return FirewallView(
        name = name,
        kind = kind,
        maker = maker,
        strength = strength,
        cpuCost = cpuCost,
    )
}

private fun ComputerState.isOverheated(): Boolean {
    return hardware.cpuMax > 0.0 && runtime.currentCpuLoad > hardware.cpuMax
}

private const val SCAN_COST: Double = 10.0
