package hackersearch.util

import com.hackwars.data.model.AttachedNetworkLink
import com.hackwars.data.model.DropItemData
import com.hackwars.data.model.ForumActivity
import com.hackwars.data.model.ForumLoginSnapshot
import com.hackwars.data.model.NetworkDefinition
import com.hackwars.data.model.NetworkNpcView
import com.hackwars.data.model.PendingPurchase
import com.hackwars.data.model.SearchBootstrapRow
import com.hackwars.data.service.GameSearchDataService
import com.hackwars.data.service.GameWorldDataService
import game.computer.persistence.BlobRef
import game.computer.persistence.JsonComputerPersistence
import game.computer.persistence.JsonComputerWebsiteSave
import game.computer.persistence.JsonComputerSaveManifest
import game.computer.persistence.TextFieldSave
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

    @Test
    fun bootstrapFromDatabase_usesCompositeDataQueryAndIndexesOnlyActivePages() = runTest {
        val service = FakeSearchDataService(
            rows = listOf(
                SearchBootstrapRow(
                    userNum = 1,
                    statsXml = """
                        <save>
                            <ip>example.com</ip>
                            <title>Alpha Title</title>
                            <body>alpha beta gamma</body>
                        </save>
                    """.trimIndent(),
                    statsJson = null,
                    statsJsonVersion = null,
                    websiteBodyText = null,
                    ip = "example.com",
                    daysSinceLastLogin = 20,
                    npc = "N",
                ),
                SearchBootstrapRow(
                    userNum = 2,
                    statsXml = """
                        <save>
                            <ip>skip.example</ip>
                            <title>Skip</title>
                            <body>skip</body>
                        </save>
                    """.trimIndent(),
                    statsJson = null,
                    statsJsonVersion = null,
                    websiteBodyText = null,
                    ip = "skip.example",
                    daysSinceLastLogin = 3,
                    npc = "N",
                ),
            )
        )

        withStartedHandler(primeFromDatabase = true, searchDataService = service) { handler ->
            val assignment = SearchAssignment(11)
            assignment.setVector("alpha beta")

            val result = handler.requestSearch(assignment)

            val results = result.getResults()
            assertEquals(1, results.size)
            val first = results[0] as SearchResult
            assertEquals("example.com", first.getAddress())
            assertEquals("Alpha Title", first.getTitle())
            assertEquals("alpha beta gamma", first.getDescription())
            assertEquals(1, service.requestCount)
            assertEquals(1, result.getSize())
            assertTrue(handler.getLoaded())
        }
    }

    @Test
    fun bootstrapFromDatabase_prefersJsonManifestAndBlobWebsiteBody() = runTest {
        val json = JsonComputerPersistence().serialize(
            JsonComputerSaveManifest(
                ip = "json.example",
                website = JsonComputerWebsiteSave(
                    title = "Json Title",
                    body = TextFieldSave(blobRef = BlobRef(path = "website/body", kind = "website-body")),
                )
            )
        )
        val service = FakeSearchDataService(
            rows = listOf(
                SearchBootstrapRow(
                    userNum = 3,
                    statsXml = null,
                    statsJson = json,
                    statsJsonVersion = 1,
                    websiteBodyText = "json alpha beta gamma",
                    ip = "json.example",
                    daysSinceLastLogin = 20,
                    npc = "N",
                )
            )
        )

        withStartedHandler(primeFromDatabase = true, searchDataService = service) { handler ->
            val assignment = SearchAssignment(12)
            assignment.setVector("json alpha")

            val result = handler.requestSearch(assignment)

            val results = result.getResults()
            assertEquals(1, results.size)
            val first = results[0] as SearchResult
            assertEquals("json.example", first.getAddress())
            assertEquals("Json Title", first.getTitle())
            assertEquals("json alpha beta gamma", first.getDescription())
        }
    }

    private suspend fun withStartedHandler(
        primeFromDatabase: Boolean = false,
        searchDataService: GameSearchDataService = FakeSearchDataService(emptyList()),
        block: suspend (SearchHandler) -> Unit,
    ) {
        val handler = SearchHandler(SearchServer(true), searchDataService = searchDataService)
        try {
            handler.start(primeFromDatabase = primeFromDatabase)
            block(handler)
        } finally {
            handler.shutdown()
            handler.join()
        }
    }

    private class FakeSearchDataService(
        val rows: List<SearchBootstrapRow>,
    ) : GameSearchDataService {
        var requestCount = 0

        override fun findBootstrapRows(): List<SearchBootstrapRow> {
            requestCount++
            return rows
        }
    }
}
