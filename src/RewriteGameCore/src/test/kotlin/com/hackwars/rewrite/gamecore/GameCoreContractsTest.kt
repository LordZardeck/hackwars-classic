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
        val registry = CommandRegistry()
            .register("requestscan") { input ->
                ScanCommand(targetStateId = input.targetStateIds.single())
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
                targetStateIds = setOf(GameStateId("TARGET-IP")),
                payloadJson = null,
                expectsResponse = true,
                metadata = CommandMetadata(),
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

        assertEquals(setOf("requestscan", "setpreferences"), registry.registeredNames())
        assertIs<ScanCommand>(scan)
        assertIs<SetPreferenceCommand>(setPreference)
    }
}
