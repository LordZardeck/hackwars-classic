package game.computer.dispatch

import com.hackwars.game.functions.FunctionTestSupport
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
            .onPredicate("any", { it.command.wireName() == "fallback" }) { seen += "fallback" }
            .build()

        val dispatcher = FallbackCommandDispatcher(primary, fallback)

        assertTrue(dispatcher.dispatch(FunctionTestSupport.noArgsCommand("legacy", sourceIp = "10.0.0.1")))
        assertTrue(dispatcher.dispatch(FunctionTestSupport.noArgsCommand("fallback", sourceIp = "10.0.0.1")))

        assertEquals(listOf("primary", "fallback"), seen)
    }
}
