package com.hackwars.rewrite.gamecore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameCoreContractsTest {
    @Test
    fun emptyComputerStateBuildsTypedDefaults() {
        val stateId = GameStateId("LOCAL-IP")

        val state = ComputerState.empty(
            id = stateId,
            playFabId = "PF-LOCALUSER",
            displayName = "Local User",
        )

        assertEquals(stateId, state.id)
        assertEquals("PF-LOCALUSER", state.identity.playFabId)
        assertEquals("LOCAL-IP", state.identity.playerIp)
        assertEquals("Local User", state.identity.displayName)
        assertTrue(state.ports.isEmpty())
        assertTrue(state.preferences.values.isEmpty())
        assertEquals(0, state.runtime.countdownSeconds)
    }

    @Test
    fun commandRegistryRoutesByWireNameAndCreatesTypedCommands() {
        val networkRepository = InMemoryNetworkDirectoryRepository.defaultWorld()
        val registry = CommandRegistry()
            .register("requestscan") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = RequestScanPayload.serializer(),
                    string = input.payloadJson ?: error("Expected request scan payload json."),
                )
                RequestScanCommand(
                    requesterStateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                    targetStateId = GameStateId(payload.targetIp ?: error("Expected target ip.")),
                )
            }
            .register("changenetwork") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = ChangeNetworkPayload.serializer(),
                    string = input.payloadJson ?: error("Expected change network payload json."),
                )
                ChangeNetworkCommand(
                    stateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                    targetNetworkName = payload.network,
                    networkDirectoryRepository = networkRepository,
                )
            }
            .register("setpreferences") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = SetPreferencePayload.serializer(),
                    string = input.payloadJson ?: error("Expected preference payload json."),
                )
                SetPreferenceCommand(
                    stateId = input.targetStateIds.single(),
                    key = payload.key,
                    value = payload.value,
                )
            }

        val scan = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "scan-1",
                commandName = "requestscan",
                targetStateIds = setOf(GameStateId("LOCAL-IP"), GameStateId("TARGET-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = RequestScanPayload.serializer(),
                    value = RequestScanPayload(
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val changeNetwork = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "network-1",
                commandName = "changenetwork",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = ChangeNetworkPayload.serializer(),
                    value = ChangeNetworkPayload(
                        ip = "LOCAL-IP",
                        network = "ProgNet",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val setPreference = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "pref-1",
                commandName = "setpreferences",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = SetPreferencePayload.serializer(),
                    value = SetPreferencePayload(
                        key = "show_tutorials",
                        value = "true",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(),
            ),
        )

        assertEquals(setOf("requestscan", "changenetwork", "setpreferences"), registry.registeredNames())
        assertIs<RequestScanCommand>(scan)
        assertIs<ChangeNetworkCommand>(changeNetwork)
        assertIs<SetPreferenceCommand>(setPreference)
    }
}
