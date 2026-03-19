package game.computer.dispatch

import game.ApplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandChainDispatcherTest {
    @Test
    fun chain_dispatches_firstMatchingRoute_only() {
        val seen = mutableListOf<String>()
        val dispatcher = CommandChainBuilder()
            .onPredicate("first", { it.function == "ping" }) { seen += "first" }
            .onPredicate("second", { it.function == "ping" }) { seen += "second" }
            .build()

        val handled = dispatcher.dispatch(ApplicationData("ping", null, 0, "10.0.0.1"))

        assertTrue(handled)
        assertEquals(listOf("first"), seen)
        assertEquals(listOf("first", "second").first(), dispatcher.routeNames().first())
    }

    @Test
    fun chain_returnsFalse_whenNothingMatches() {
        val dispatcher = CommandChainBuilder()
            .onPredicate("never", { false }) { }
            .build()

        val handled = dispatcher.dispatch(ApplicationData("noop", null, 0, "10.0.0.1"))

        assertFalse(handled)
    }
}
