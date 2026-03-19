package hackersearch.util

import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResult
import hackersearch.server.SearchServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

class SearchHandlerTest {
    @Test
    fun indexPage_queuesTaskThatProducesSearchableResult() {
        val handler = TestSearchHandler()

        handler.indexPage("Alpha <b>Title</b>", "example.com", "alpha <b>beta</b> &amp; gamma")
        drainQueuedTasks(handler)

        val assignment = SearchAssignment(7)
        assignment.setVector("alpha beta")

        val result = handler.requestSearch(assignment)

        val results = result.getResults()
        assertEquals(1, results.size)
        val first = results[0] as SearchResult
        assertEquals("example.com", first.getAddress())
        assertEquals("Alpha Title", first.getTitle())
        assertEquals("alpha beta  gamma", first.getDescription())
        assertEquals(1, result.getSize())
        assertEquals(0, result.getCurrent())
    }

    @Test
    fun indexPageTask_replacesExistingEntryForSameAddress() {
        val handler = TestSearchHandler()

        handler.indexPage("Old Title", "example.com", "alpha beta")
        drainQueuedTasks(handler)
        handler.indexPage("New Title", "example.com", "gamma delta")
        drainQueuedTasks(handler)

        val alphaSearch = SearchAssignment(8)
        alphaSearch.setVector("alpha")
        assertTrue(handler.requestSearch(alphaSearch).getResults().isEmpty())

        val gammaSearch = SearchAssignment(9)
        gammaSearch.setVector("gamma")
        val gammaResult = handler.requestSearch(gammaSearch)

        val gammaResults = gammaResult.getResults()
        assertFalse(gammaResults.isEmpty())
        val first = gammaResults[0] as SearchResult
        assertEquals("New Title", first.getTitle())
        assertEquals("gamma delta", first.getDescription())
    }

    private fun drainQueuedTasks(handler: SearchHandler) {
        @Suppress("UNCHECKED_CAST")
        val executeStack = field(SearchHandler::class.java, "ExecuteStack").get(handler) as ArrayList<Task>
        val tasks = ArrayList(executeStack)
        executeStack.clear()
        tasks.forEach(Task::execute)
    }

    private fun field(type: Class<*>, name: String): Field {
        return type.getDeclaredField(name).apply {
            isAccessible = true
        }
    }

    private class TestSearchHandler : SearchHandler(SearchServer(true)) {
        override fun run() {
            // Keep tests deterministic by skipping the constructor-started background loop.
        }
    }
}
