package hackersearch.util

import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResult
import hackersearch.server.SearchServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHandlerTest {
    @Test
    fun indexPage_queuesTaskThatProducesSearchableResult() = runTest {
        withStartedHandler { handler ->
            handler.indexPage("Alpha <b>Title</b>", "example.com", "alpha <b>beta</b> &amp; gamma")
            handler.awaitIdle()

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
            assertTrue(handler.getLoaded())
        }
    }

    @Test
    fun indexPage_replacesExistingEntryForSameAddress() = runTest {
        withStartedHandler { handler ->
            handler.indexPage("Old Title", "example.com", "alpha beta")
            handler.awaitIdle()
            handler.indexPage("New Title", "example.com", "gamma delta")
            handler.awaitIdle()

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
    }

    private suspend fun withStartedHandler(block: suspend (SearchHandler) -> Unit) {
        val handler = SearchHandler(SearchServer(true))
        try {
            handler.start(primeFromDatabase = false)
            block(handler)
        } finally {
            handler.shutdown()
            handler.join()
        }
    }
}
