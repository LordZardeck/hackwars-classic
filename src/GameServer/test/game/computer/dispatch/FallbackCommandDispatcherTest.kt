package game.computer.dispatch

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackCommandDispatcherTest {
    @Test
    fun fallback_dispatches_primaryFirst_thenFallsBack() {
        val seen = mutableListOf<String>()
        val primary = CommandRegistry()
            .register("legacy") { seen += "primary" }
        val fallback = CommandChainBuilder()
            .onPredicate("any", { it.function == "fallback" }) { seen += "fallback" }
            .build()

        val dispatcher = FallbackCommandDispatcher(primary, fallback)

        assertTrue(dispatcher.dispatch(ApplicationData("legacy", null, 0, "10.0.0.1")))
        assertTrue(dispatcher.dispatch(ApplicationData("fallback", null, 0, "10.0.0.1")))

        assertEquals(listOf("primary", "fallback"), seen)
    }
}
