package game

import com.hackwars.game.functions.FunctionTestSupport
import game.computer.dispatch.CommandDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyRunLoopApplicationDataRouterTest {
    @Test
    fun dispatch_invokesExactDispatcherBeforeAnyLegacyHandlers() {
        val calls = mutableListOf<String>()
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean {
                val command = applicationData.command.wireName()
                calls += "dispatcher:$command"
                return command == "handled"
            }
        }
        val legacyHandler = legacyHandler { _, _, _ ->
            calls += "legacy"
            true
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            computer(),
            FunctionTestSupport.noArgsCommand("handled", sourceIp = "10.0.0.1"),
            0,
            dispatcher,
            listOf(legacyHandler)
        )

        assertTrue(handled)
        assertEquals(listOf("dispatcher:handled"), calls)
    }

    @Test
    fun dispatch_usesLegacyHandlersInOrderAndStopsAfterFirstMatch() {
        val calls = mutableListOf<String>()
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean {
                calls += "dispatcher:${applicationData.command.wireName()}"
                return false
            }
        }
        val first = legacyHandler { _, applicationData, resolvedPort ->
            calls += "first:${applicationData.command.wireName()}:$resolvedPort"
            false
        }
        val second = legacyHandler { _, applicationData, resolvedPort ->
            calls += "second:${applicationData.command.wireName()}:$resolvedPort"
            true
        }
        val third = legacyHandler { _, _, _ ->
            calls += "third"
            true
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            computer(),
            FunctionTestSupport.noArgsCommand("legacy", sourceIp = "10.0.0.1"),
            22,
            dispatcher,
            listOf(first, second, third)
        )

        assertTrue(handled)
        assertEquals(
            listOf(
                "dispatcher:legacy",
                "first:legacy:22",
                "second:legacy:22"
            ),
            calls
        )
    }

    @Test
    fun dispatch_fallsThroughAllLegacyHandlersBeforeReturningFalse() {
        val calls = mutableListOf<String>()
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean {
                calls += "dispatcher:${applicationData.command.wireName()}"
                return false
            }
        }
        val first = legacyHandler { _, applicationData, resolvedPort ->
            calls += "first:${applicationData.command.wireName()}:$resolvedPort"
            false
        }
        val second = legacyHandler { _, applicationData, resolvedPort ->
            calls += "second:${applicationData.command.wireName()}:$resolvedPort"
            false
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            computer(),
            FunctionTestSupport.noArgsCommand("unhandled", sourceIp = "10.0.0.1"),
            11,
            dispatcher,
            listOf(first, second)
        )

        assertFalse(handled)
        assertEquals(
            listOf(
                "dispatcher:unhandled",
                "first:unhandled:11",
                "second:unhandled:11"
            ),
            calls
        )
    }

    private fun computer(): Computer = FunctionTestSupport.baseComputer()

    private fun legacyHandler(
        block: (Computer, ApplicationData, Int) -> Boolean
    ): LegacyApplicationDataHandler {
        return object : LegacyApplicationDataHandler {
            override fun dispatch(
                computer: Computer,
                applicationData: ApplicationData,
                resolvedPort: Int
            ): Boolean = block(computer, applicationData, resolvedPort)
        }
    }
}
