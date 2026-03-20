package hackersearch.server

import hackersearch.assignments.IndexPageAssignment
import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResultAssignment
import hackersearch.util.SearchHandler
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SearchServerTest {
    @Before
    fun resetSingletonsBefore() {
        resetSingletons()
    }

    @After
    fun resetSingletonsAfter() {
        resetSingletons()
    }

    @Test
    fun getInstance_reusesSingletonAndConfiguredSearchHandler() {
        val configuredHandler = mock<SearchHandler>()
        SearchServer.installSearchHandlerForTesting(configuredHandler)

        val first = SearchServer.getInstance()
        val second = SearchServer.getInstance()

        assertSame(first, second)
        assertSame(configuredHandler, first.getSearchHandlerForTesting())
    }

    @Test
    fun dispatchPacket_tracksAssignmentsById() {
        val server = SearchServer(true)
        val result = SearchResultAssignment(42)

        assertFalse(server.hasResult(42))

        server.dispatchPacket(result, 7)

        assertTrue(server.hasResult(42))
        assertSame(result, server.getResult(42))
    }

    @Test
    fun requestSearch_delegatesToConfiguredHandler() {
        val server = SearchServer(true)
        val handler = mock<SearchHandler>()
        val assignment = SearchAssignment(12)
        val result = SearchResultAssignment(12)
        whenever(handler.requestSearch(same(assignment))).thenReturn(result)
        server.setSearchHandlerForTesting(handler)

        val returned = server.requestSearch(assignment)

        assertSame(result, returned)
        verify(handler).requestSearch(same(assignment))
    }

    @Test
    fun returnAssignment_forwardsOnlyIndexPageAssignments() {
        val server = SearchServer(true)
        val handler = mock<SearchHandler>()
        server.setSearchHandlerForTesting(handler)
        val indexPageAssignment = IndexPageAssignment(4, "title", "ip", "body")

        server.returnAssignment(indexPageAssignment)
        verify(handler).indexPage("title", "ip", "body")

        val otherHandler = mock<SearchHandler>()
        server.setSearchHandlerForTesting(otherHandler)
        server.returnAssignment(SearchAssignment(9))
        verifyNoInteractions(otherHandler)
    }

    private fun resetSingletons() {
        SearchServer.resetForTests()
    }
}
