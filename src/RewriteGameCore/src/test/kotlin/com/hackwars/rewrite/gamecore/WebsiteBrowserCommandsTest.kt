package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebsiteBrowserCommandsTest {
    @Test
    fun requestPageReturnsSavedWebsiteWithoutDeltas() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localWebsiteState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestPageCommand(stateId),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("Home Page", response.title)
        assertEquals("<html>Welcome</html>", response.body)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun savePagePersistsWebsiteAndRejectsOversizedBodies() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(stateId to localWebsiteState(stateId)),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = SavePageCommand(
                stateId = stateId,
                title = "Rewritten Page",
                body = "<html>Updated</html>",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val updatedState = repository.load(stateId)

        requireNotNull(updatedState)
        assertEquals("Rewritten Page", response.title)
        assertEquals("<html>Updated</html>", updatedState.website.body)
        assertEquals(setOf("website"), publisher.deltas.single().second.deltaKeys)
        assertIs<StateSectionsDeltaProjection>(publisher.deltas.single().second.projection)

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = SavePageCommand(
                    stateId = stateId,
                    title = "Too Big",
                    body = "x".repeat(30_001),
                ),
                metadata = CommandMetadata(connectionId = "conn-1"),
                publisher = publisher,
            )
        }
    }

    @Test
    fun requestWebpageAndSubmitReturnStaticPageOrFallbackWithoutDeltas() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val offlineId = GameStateId("OFFLINE-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to localWebsiteState(sourceId),
                targetId to targetWebsiteState(targetId),
                offlineId to offlineWebsiteState(offlineId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", sourceId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val page = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = targetId,
                parameters = mapOf("q" to "test"),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val submit = dispatcher.request(
            command = SubmitWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = targetId,
                parameters = mapOf("field" to "value"),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val fallback = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = offlineId,
                parameters = emptyMap(),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("Remote Shop", page.title)
        assertEquals(false, page.fallback)
        assertEquals(listOf("merchant.bin"), page.storeFiles.map { it.name })
        assertEquals("Remote Shop", submit.title)
        assertFalse(submit.fallback)
        assertEquals(LEGACY_SERVER_NOT_FOUND_TITLE, fallback.title)
        assertEquals(LEGACY_SERVER_NOT_FOUND_BODY, fallback.body)
        assertTrue(fallback.fallback)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun requestWebpageAndSubmitExecuteHttpHooksAndFallbackToStaticPageOnFailure() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val hookedId = GameStateId("HOOKED-IP")
        val brokenId = GameStateId("BROKEN-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to localWebsiteState(sourceId),
                hookedId to hookedWebsiteState(hookedId),
                brokenId to brokenWebsiteState(brokenId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", sourceId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)
        val runtime = HackScriptHttpHookRuntime()

        val page = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = hookedId,
                parameters = mapOf("q" to "alpha"),
                httpHookRuntime = runtime,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val submit = dispatcher.request(
            command = SubmitWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = hookedId,
                parameters = mapOf("mode" to "posted"),
                httpHookRuntime = runtime,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val broken = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = brokenId,
                parameters = mapOf("q" to "alpha"),
                httpHookRuntime = runtime,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("<html>LOCAL-IP-enter</html>", page.body)
        assertEquals(listOf("merchant.bin"), page.storeFiles.map { it.name })
        assertFalse(page.fallback)
        assertEquals("<html>posted-enter</html>", submit.body)
        assertTrue(submit.storeFiles.isEmpty())
        assertFalse(submit.fallback)
        assertEquals("<html><?first?>-<?second?></html>", broken.body)
        assertFalse(broken.fallback)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun exitIsLifecycleOnlyAndProducesNoMutationsOrDeltas() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to localWebsiteState(sourceId),
                targetId to targetWebsiteState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", sourceId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.dispatch(
            command = ExitWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = targetId,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertTrue(publisher.deltas.isEmpty())
        assertEquals(targetWebsiteState(targetId), repository.load(targetId))
    }

    @Test
    fun voteRequiresAllGatesAndAtomicallyUpdatesWebsiteAndHttpExperience() = runTest {
        val voterId = GameStateId("VOTER-IP")
        val targetId = GameStateId("TARGET-IP")
        val lowLevelId = GameStateId("LOW-IP")
        val noVotesId = GameStateId("NOVOTES-IP")
        val offlineId = GameStateId("OFFLINE-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                voterId to localWebsiteState(voterId),
                targetId to targetWebsiteState(targetId),
                lowLevelId to localWebsiteState(lowLevelId).copy(
                    stats = PlayerStatsState(totalLevel = 1, noobProtectionLevel = 3),
                ),
                noVotesId to localWebsiteState(noVotesId).copy(
                    website = localWebsiteState(noVotesId).website.copy(votesAvailable = 0),
                ),
                offlineId to offlineWebsiteState(offlineId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("voter-conn", voterId)
        interests.register("target-conn", targetId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = VoteForWebsiteCommand(voterStateId = voterId, targetStateId = targetId),
            metadata = CommandMetadata(connectionId = "voter-conn"),
            publisher = publisher,
        )

        assertEquals(1, response.votesAvailableAfter)
        assertEquals(8, response.targetVoteCountAfter)
        assertEquals(500, response.targetHttpExperienceAfter)
        assertEquals(listOf(setOf("website"), setOf("website", "stats")), publisher.deltas.map { it.second.deltaKeys })
        val targetProjection = assertIs<StateSectionsDeltaProjection>(publisher.deltas.last().second.projection)
        assertNotNull(targetProjection.website)
        assertNotNull(targetProjection.stats)

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = VoteForWebsiteCommand(voterStateId = lowLevelId, targetStateId = targetId),
                metadata = CommandMetadata(connectionId = "voter-conn"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = VoteForWebsiteCommand(voterStateId = noVotesId, targetStateId = targetId),
                metadata = CommandMetadata(connectionId = "voter-conn"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = VoteForWebsiteCommand(voterStateId = voterId, targetStateId = voterId),
                metadata = CommandMetadata(connectionId = "voter-conn"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = VoteForWebsiteCommand(voterStateId = voterId, targetStateId = offlineId),
                metadata = CommandMetadata(connectionId = "voter-conn"),
            )
        }
    }

    private fun localWebsiteState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            website = WebsiteState(
                title = "Home Page",
                body = "<html>Welcome</html>",
                votesAvailable = 2,
                voteCount = 3,
            ),
            stats = PlayerStatsState(
                totalLevel = 5,
                noobProtectionLevel = 3,
            ),
            economy = EconomyState(
                pettyCash = 500.0,
                bankMoney = 250.0,
                defaultBankPort = 6,
            ),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(),
            ),
        )
    }

    private fun targetWebsiteState(stateId: GameStateId): ComputerState {
        val filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .ensureDirectory("/Store")
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Store", "merchant.bin"),
                    name = "merchant.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "compiled merchant",
                    price = 196.0,
                    quantity = 2,
                ),
            )
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            filesystem = filesystem,
            website = WebsiteState(
                title = "Remote Shop",
                body = "<html>Remote</html>",
                voteCount = 7,
            ),
            economy = EconomyState(
                pettyCash = 100.0,
                defaultBankPort = 6,
            ),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(),
            ),
        )
    }

    private fun hookedWebsiteState(stateId: GameStateId): ComputerState {
        val filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .ensureDirectory("/Store")
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Store", "merchant.bin"),
                    name = "merchant.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "compiled merchant",
                    price = 196.0,
                    quantity = 2,
                ),
            )
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            filesystem = filesystem,
            website = WebsiteState(
                title = "Hooked Site",
                body = "<html><?first?>-<?second?></html>",
                voteCount = 7,
            ),
            economy = EconomyState(defaultBankPort = 6),
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(
                    scriptBundle = ProgramScriptBundle(
                        family = ScriptFamily.HTTP,
                        scriptsBySlot = linkedMapOf(
                            ProgramScriptSlot.ENTER to """
                                int main() {
                                    replaceContent("first", getVisitorIP());
                                    replaceContent("second", "enter");
                                    return 0;
                                }
                            """.trimIndent(),
                            ProgramScriptSlot.EXIT to """
                                int main() {
                                    return 0;
                                }
                            """.trimIndent(),
                            ProgramScriptSlot.SUBMIT to """
                                int main() {
                                    replaceContent("first", getParameter("mode"));
                                    hideStore();
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun brokenWebsiteState(stateId: GameStateId): ComputerState {
        return hookedWebsiteState(stateId).copy(
            ports = listOf(
                bankingPort(),
                ftpPort(),
                httpPort(
                    scriptBundle = ProgramScriptBundle(
                        family = ScriptFamily.HTTP,
                        scriptsBySlot = linkedMapOf(
                            ProgramScriptSlot.ENTER to """
                                int main() {
                                    triggerWatch(1);
                                    return 0;
                                }
                            """.trimIndent(),
                            ProgramScriptSlot.EXIT to "int main() { return 0; }",
                            ProgramScriptSlot.SUBMIT to "int main() { return 0; }",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun offlineWebsiteState(stateId: GameStateId): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            website = WebsiteState(
                title = "Offline",
                body = "<html>Offline</html>",
                voteCount = 1,
            ),
            economy = EconomyState(defaultBankPort = 6),
            ports = listOf(
                bankingPort(),
                ftpPort(),
            ),
        )
    }

    private fun bankingPort(): PortState {
        return PortState(
            number = 6,
            type = "banking",
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "bank.bin",
                kind = ApplicationKind.BANKING,
                binaryPath = "/Public/bank.bin",
                banking = true,
            ),
        )
    }

    private fun ftpPort(): PortState {
        return PortState(
            number = 21,
            type = "ftp",
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "ftp.bin",
                kind = ApplicationKind.FTP,
                binaryPath = "/system/ftp.bin",
            ),
        )
    }

    private fun httpPort(
        scriptBundle: ProgramScriptBundle? = null,
    ): PortState {
        return PortState(
            number = 80,
            type = "http",
            enabled = true,
            defaultPort = true,
            installedApplication = InstalledApplication(
                name = "http.bin",
                kind = ApplicationKind.HTTP,
                binaryPath = "/system/http.bin",
                scriptBundle = scriptBundle,
            ),
        )
    }
}
