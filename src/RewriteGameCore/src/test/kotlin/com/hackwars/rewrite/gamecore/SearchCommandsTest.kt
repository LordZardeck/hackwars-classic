package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class SearchCommandsTest {
    @Test
    fun requestsearchRanksSanitizedBodiesAndAppliesLegacyVisibilityRules() = runTest {
        val stateId = GameStateId("SEARCHER-IP")
        val now = 2_000_000_000L
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(stateId to ComputerState.empty(stateId, playerIp = stateId.value)),
            ),
            interestRegistry = InMemoryInterestRegistry(),
        )

        val response = dispatcher.request(
            command = RequestSearchCommand(
                requesterStateId = stateId,
                query = "alpha beta the",
                startIndex = 0,
                searchCatalogRepository = InMemorySearchCatalogRepository(
                    documents = listOf(
                        SearchableWebsiteDocument(
                            address = "old-ip",
                            title = "Alpha <b>Title</b>",
                            body = "alpha <b>beta</b> &amp; gamma",
                            searchable = true,
                            lastLoginAtEpochMillis = now - 15.days.inWholeMilliseconds,
                            isNpc = false,
                        ),
                        SearchableWebsiteDocument(
                            address = "npc-ip",
                            title = "Npc Title",
                            body = "alpha",
                            searchable = true,
                            lastLoginAtEpochMillis = null,
                            isNpc = true,
                        ),
                        SearchableWebsiteDocument(
                            address = "active-ip",
                            title = "Active Title",
                            body = "alpha beta alpha beta",
                            searchable = true,
                            lastLoginAtEpochMillis = now - 1.days.inWholeMilliseconds,
                            isNpc = false,
                        ),
                        SearchableWebsiteDocument(
                            address = "no-http",
                            title = "No Http",
                            body = "alpha beta alpha beta alpha",
                            searchable = false,
                            lastLoginAtEpochMillis = now - 30.days.inWholeMilliseconds,
                            isNpc = false,
                        ),
                    ),
                ),
                clock = { now },
            ),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals(listOf("alpha", "beta"), response.queryTerms)
        assertEquals(0, response.startIndex)
        assertEquals(2, response.totalSize)
        assertEquals(listOf("old-ip", "npc-ip"), response.results.map { it.address })
        assertEquals("Alpha Title", response.results.first().title)
        assertEquals("alpha beta  gamma", response.results.first().description)
    }

    @Test
    fun requestsearchSupportsAbsoluteOffsetsAndEmptyQueries() = runTest {
        val stateId = GameStateId("SEARCHER-IP")
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(stateId to ComputerState.empty(stateId, playerIp = stateId.value)),
            ),
            interestRegistry = InMemoryInterestRegistry(),
        )
        val repository = InMemorySearchCatalogRepository(
            documents = listOf(
                SearchableWebsiteDocument(
                    address = "first-ip",
                    title = "First",
                    body = "needle alpha",
                    searchable = true,
                    lastLoginAtEpochMillis = 0L,
                    isNpc = true,
                ),
                SearchableWebsiteDocument(
                    address = "second-ip",
                    title = "Second",
                    body = "needle beta",
                    searchable = true,
                    lastLoginAtEpochMillis = 0L,
                    isNpc = true,
                ),
            ),
        )

        val paged = dispatcher.request(
            command = RequestSearchCommand(
                requesterStateId = stateId,
                query = "needle",
                startIndex = 1,
                searchCatalogRepository = repository,
                clock = { 30.days.inWholeMilliseconds },
            ),
            publisher = NoOpGameStatePublisher,
        )
        val empty = dispatcher.request(
            command = RequestSearchCommand(
                requesterStateId = stateId,
                query = "   ",
                startIndex = 7,
                searchCatalogRepository = repository,
            ),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals(2, paged.totalSize)
        assertEquals(listOf("second-ip"), paged.results.map { it.address })
        assertEquals(7, empty.startIndex)
        assertTrue(empty.results.isEmpty())
        assertTrue(empty.queryTerms.isEmpty())
    }

    @Test
    fun bootstrapRecordsLastLoginBeforeReturningSnapshot() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to ComputerState.empty(
                    id = stateId,
                    playFabId = "PF-LOCALUSER",
                    playerIp = stateId.value,
                ),
            ),
        )
        val interests = InMemoryInterestRegistry()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val bootstrap = dispatcher.request(
            command = GameSessionBootstrapCommand(
                stateId = stateId,
                playFabId = "PF-LOCALUSER",
                interestRegistry = interests,
                clock = { 123_456L },
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = NoOpGameStatePublisher,
        )

        assertEquals(123_456L, bootstrap.state.identity.lastLoginAtEpochMillis)
        assertEquals(123_456L, repository.load(stateId)?.identity?.lastLoginAtEpochMillis)
        assertEquals(setOf(stateId), interests.subscriptionsFor("conn-1"))
    }
}
