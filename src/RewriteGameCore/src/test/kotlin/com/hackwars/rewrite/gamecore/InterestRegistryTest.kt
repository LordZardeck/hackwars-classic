package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InterestRegistryTest {
    @Test
    fun oneConnectionCanSubscribeToMultipleStatesAndFanoutToManySubscribers() = runTest {
        val registry = InMemoryInterestRegistry()
        val local = GameStateId("LOCAL-IP")
        val target = GameStateId("TARGET-IP")

        registry.register("conn-a", local)
        registry.register("conn-a", target)
        registry.register("conn-b", local)

        assertEquals(setOf(local, target), registry.subscriptionsFor("conn-a"))
        assertEquals(setOf("conn-a", "conn-b"), registry.subscribersFor(local))
        assertEquals(setOf("conn-a"), registry.subscribersFor(target))
    }

    @Test
    fun unregisterConnectionRemovesReverseSubscriptionsWithoutLeaks() = runTest {
        val registry = InMemoryInterestRegistry()
        val local = GameStateId("LOCAL-IP")
        val target = GameStateId("TARGET-IP")

        registry.register("conn-a", local)
        registry.register("conn-a", target)
        registry.register("conn-b", local)

        registry.unregister("conn-b", local)
        registry.unregisterConnection("conn-a")

        assertEquals(emptySet(), registry.subscriptionsFor("conn-a"))
        assertEquals(emptySet(), registry.subscribersFor(local))
        assertEquals(emptySet(), registry.subscribersFor(target))
    }
}
