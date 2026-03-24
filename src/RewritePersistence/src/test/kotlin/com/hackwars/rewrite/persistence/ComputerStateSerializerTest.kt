package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.PreferenceSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComputerStateSerializerTest {
    private val serializer = ComputerStateSerializer()

    @Test
    fun roundTripsTypedComputerStateToUtf8JsonBytes() {
        val state = ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).copy(
            ports = listOf(
                PortState(number = 22, type = "ssh"),
                PortState(number = 80, type = "http"),
            ),
        )

        val reloaded = serializer.decodeState(serializer.encodeState(state))

        assertEquals(state, reloaded)
    }

    @Test
    fun roundTripsTypedComputerEventsToUtf8JsonBytes() {
        val event = PreferenceSetEvent(
            key = "show_clock",
            value = "true",
        )

        val reloaded = serializer.decodeEvent(serializer.encodeEvent(event))

        assertIs<PreferenceSetEvent>(reloaded)
        assertEquals(event, reloaded)
    }
}
