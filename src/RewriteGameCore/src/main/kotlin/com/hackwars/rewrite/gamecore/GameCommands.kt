package com.hackwars.rewrite.gamecore

class GameSessionBootstrapCommand(
    private val stateId: GameStateId,
    private val playFabId: String,
    private val interestRegistry: InterestRegistry,
) : RequestCommand<GameSessionBootstrapResult> {
    override val name: String = "game-session-bootstrap"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): GameSessionBootstrapResult {
        val connectionId = checkNotNull(context.connectionId) {
            "GameSessionBootstrapCommand requires a connection id."
        }
        interestRegistry.register(connectionId, stateId)
        val state = context.loadState(stateId)
            ?: ComputerState.empty(
                id = stateId,
                playFabId = playFabId,
                playerIp = stateId.value,
            )
        return GameSessionBootstrapResult(state = state)
    }
}

class ScanCommand(
    private val targetStateId: GameStateId,
) : RequestCommand<ScanResponse> {
    override val name: String = "requestscan"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(targetStateId)

    override suspend fun execute(context: CommandContext): ScanResponse {
        val state = context.loadState(targetStateId)
            ?: ComputerState.empty(targetStateId, playerIp = targetStateId.value)
        return ScanResponse(
            targetIp = targetStateId.value,
            openPorts = state.ports.filter { it.enabled }.map { it.number }.sorted(),
        )
    }
}

class SetPreferenceCommand(
    private val stateId: GameStateId,
    private val key: String,
    private val value: String,
) : RequestCommand<SetPreferenceCommandResponse> {
    override val name: String = "setpreferences"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SetPreferenceCommandResponse {
        val updatedState = context.appendEvents(
            id = stateId,
            events = listOf(
                PreferenceSetEvent(
                    key = key,
                    value = value,
                ),
            ),
        )
        return SetPreferenceCommandResponse(
            key = key,
            value = value,
            version = updatedState.version,
        )
    }
}
