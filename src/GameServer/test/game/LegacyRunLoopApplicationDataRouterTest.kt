package game

import game.computer.dispatch.CommandDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class LegacyRunLoopApplicationDataRouterTest {
    @Test
    fun dispatch_invokesExactDispatcherBeforeAnyLegacyHandlers() {
        val calls = mutableListOf<String>()
        val dispatcher = object : CommandDispatcher {
            override fun dispatch(applicationData: ApplicationData): Boolean {
                calls += "dispatcher:${applicationData.function}"
                return applicationData.function == "handled"
            }
        }
        val legacyHandler = LegacyApplicationDataHandler { _, _, _ ->
            calls += "legacy"
            true
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("handled", null, 0, "10.0.0.1"),
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
                calls += "dispatcher:${applicationData.function}"
                return false
            }
        }
        val first = LegacyApplicationDataHandler { _, applicationData, resolvedPort ->
            calls += "first:${applicationData.function}:$resolvedPort"
            false
        }
        val second = LegacyApplicationDataHandler { _, applicationData, resolvedPort ->
            calls += "second:${applicationData.function}:$resolvedPort"
            true
        }
        val third = LegacyApplicationDataHandler { _, _, _ ->
            calls += "third"
            true
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("legacy", null, 0, "10.0.0.1"),
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
                calls += "dispatcher:${applicationData.function}"
                return false
            }
        }
        val first = LegacyApplicationDataHandler { _, applicationData, resolvedPort ->
            calls += "first:${applicationData.function}:$resolvedPort"
            false
        }
        val second = LegacyApplicationDataHandler { _, applicationData, resolvedPort ->
            calls += "second:${applicationData.function}:$resolvedPort"
            false
        }

        val handled = LegacyRunLoopApplicationDataRouter.dispatch(
            mock<Computer>(),
            ApplicationData("unhandled", null, 0, "10.0.0.1"),
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
}
