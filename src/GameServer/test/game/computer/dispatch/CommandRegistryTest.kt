package game.computer.dispatch

import com.hackwars.game.functions.Function
import com.hackwars.game.functions.FunctionTestSupport
import game.ApplicationData
import game.Computer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CommandRegistryTest {
    @Test
    fun dispatch_runsRegisteredHandler_andReturnsTrue() {
        val seen = mutableListOf<String>()
        val registry = CommandRegistry()
            .register("ping") { applicationData ->
                seen += applicationData.sourceIP
            }

        val handled = registry.dispatch(FunctionTestSupport.noArgsCommand("ping", sourceIp = "10.0.0.8"))

        assertTrue(handled)
        assertEquals(listOf("10.0.0.8"), seen)
    }

    @Test
    fun dispatch_returnsFalseForUnknownCommand() {
        val registry = CommandRegistry()

        val handled = registry.dispatch(FunctionTestSupport.noArgsCommand("unknown", sourceIp = "10.0.0.8"))

        assertFalse(handled)
    }

    @Test
    fun fromFunctions_wrapsLegacyFunctionMap() {
        val computer = mock<Computer>()
        var invocations = 0
        val legacy = object : Function(computer) {
            override fun execute(applicationData: ApplicationData) {
                invocations += 1
                assertEquals("legacy", applicationData.command.wireName())
            }
        }
        val registry = CommandRegistry.fromFunctions(mapOf("legacy" to legacy))

        val handled = registry.dispatch(FunctionTestSupport.noArgsCommand("legacy", sourceIp = "10.0.0.8"))

        assertTrue(handled)
        assertEquals(1, invocations)
    }
}
