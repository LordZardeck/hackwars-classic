package game

import game.computer.dispatch.CommandDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class LegacyRunLoopApplicationDataRouterTest {
    @Test
    fun dispatch_usesExactDispatcherBeforeLegacyHandlers() {
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean {
                return applicationData.function == "handled"
            }
        }
        val legacyHandler = LegacyApplicationDataHandler { _, _, _ ->
            throw AssertionError("legacy handler should not run when exact dispatcher handles the command")
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("handled", null, 0, "10.0.0.1"),
            0,
            dispatcher,
            listOf(legacyHandler)
        )

        assertTrue(handled)
    }

    @Test
    fun dispatch_usesFirstMatchingLegacyHandlerInOrder() {
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean = false
        }
        var firstCalled = false
        var secondCalled = false
        val first = LegacyApplicationDataHandler { _, applicationData, _ ->
            firstCalled = true
            applicationData.function == "legacy"
        }
        val second = LegacyApplicationDataHandler { _, _, _ ->
            secondCalled = true
            true
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("legacy", null, 0, "10.0.0.1"),
            22,
            dispatcher,
            listOf(first, second)
        )

        assertTrue(handled)
        assertTrue(firstCalled)
        assertFalse(secondCalled)
    }

    @Test
    fun dispatch_returnsFalseWhenNothingHandlesCommand() {
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean = false
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("unhandled", null, 0, "10.0.0.1"),
            11,
            dispatcher,
            emptyList()
        )

        assertFalse(handled)
    }
}
