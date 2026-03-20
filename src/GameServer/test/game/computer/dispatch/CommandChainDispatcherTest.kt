package game.computer.dispatch

import com.hackwars.game.functions.FunctionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandChainDispatcherTest {
    @Test
    fun chain_dispatches_firstMatchingRoute_only() {
        val seen = mutableListOf<String>()
        val dispatcher = CommandChainBuilder()
            .onPredicate("first", { it.command.wireName() == "ping" }) { seen += "first" }
            .onPredicate("second", { it.command.wireName() == "ping" }) { seen += "second" }
            .build()

        val handled = dispatcher.dispatch(FunctionTestSupport.noArgsCommand("ping", sourceIp = "10.0.0.1"))

        assertTrue(handled)
        assertEquals(listOf("first"), seen)
        assertEquals(listOf("first", "second").first(), dispatcher.routeNames().first())
    }

    @Test
    fun chain_returnsFalse_whenNothingMatches() {
        val dispatcher = CommandChainBuilder()
            .onPredicate("never", { false }) { }
            .build()

        val handled = dispatcher.dispatch(FunctionTestSupport.noArgsCommand("noop", sourceIp = "10.0.0.1"))

        assertFalse(handled)
    }
}
