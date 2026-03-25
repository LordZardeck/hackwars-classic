package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.StringHookValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WatchExecutionCommandsTest {
    @Test
    fun externalTriggerWatchCanStartZombieAttackAndChargeTheWatchedHost() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val watchHostId = GameStateId("WATCH-IP")
        val victimId = GameStateId("VICTIM-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                watchHostId to watchHostState(
                    stateId = watchHostId,
                    pettyCash = 60.0,
                    watches = listOf(
                        installedWatch(
                            contents = """
                                int main() {
                                    zombieAttack("legacy-parent", 12, "VICTIM-IP", 25);
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                    additionalPorts = listOf(
                        attackPort(
                            scriptBundle = ProgramScriptBundle(
                                family = ScriptFamily.ATTACK,
                                scriptsBySlot = linkedMapOf(
                                    ProgramScriptSlot.INITIALIZE to """int main() { zombie("WATCH-IP"); return 0; }""",
                                ),
                            ),
                        ),
                    ),
                ),
                victimId to zombieTargetState(victimId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("watch-conn", watchHostId)
            register("victim-conn", victimId)
        }
        val publisher = RecordingGameStatePublisher()
        val registry = InMemoryAttackProgramRegistry()
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            attackProgramRegistry = registry,
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )

        val response = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = watchHostId,
                selector = TriggerSelector.ByIndex(0),
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "watch-zombie-start"),
            publisher = publisher,
        )

        val updatedWatchHost = requireNotNull(repository.load(watchHostId))
        val updatedVictim = requireNotNull(repository.load(victimId))
        val session = updatedWatchHost.combat.activeAttacksBySourcePort[12]

        assertTrue(response.accepted)
        assertTrue(response.executed)
        assertTrue(
            session != null,
            "watchHost=$updatedWatchHost victim=$updatedVictim uiEvents=${publisher.uiEvents}",
        )
        val activeSession = requireNotNull(session)
        assertEquals(40.0, updatedWatchHost.economy.pettyCash)
        assertEquals(8.0, updatedWatchHost.runtime.currentCpuLoad)
        assertEquals(AttackMode.ZOMBIE, activeSession.attackMode)
        assertEquals(watchHostId, activeSession.controllerStateId)
        assertEquals(watchHostId, activeSession.authorizedZombieStateId)
        assertEquals(victimId, activeSession.targetStateId)
        assertEquals(watchHostId, updatedVictim.combat.incomingAttacksByTargetPort.getValue(25).attackerStateId)
        assertTrue(
            publisher.deltas.any { recipientsAndDelta ->
                recipientsAndDelta.first == setOf("watch-conn") &&
                    recipientsAndDelta.second.deltaKeys.containsAll(setOf("economy", "ports", "combat", "runtime"))
            },
        )
        assertTrue(
            publisher.deltas.any { recipientsAndDelta ->
                recipientsAndDelta.first == setOf("victim-conn") &&
                    recipientsAndDelta.second.deltaKeys == setOf("combat")
            },
        )
        assertTrue(publisher.uiEvents.isEmpty())
    }

    @Test
    fun watchTriggeredZombieStartFailuresReuseZombieUiMappingWithoutCorrelatedZombieResponse() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val watchHostId = GameStateId("WATCH-IP")
        val victimId = GameStateId("VICTIM-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                watchHostId to watchHostState(
                    stateId = watchHostId,
                    pettyCash = 10.0,
                    watches = listOf(
                        installedWatch(
                            contents = """
                                int main() {
                                    zombieAttack("legacy-parent", 12, "VICTIM-IP", 25);
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                    additionalPorts = listOf(
                        attackPort(
                            scriptBundle = ProgramScriptBundle(
                                family = ScriptFamily.ATTACK,
                                scriptsBySlot = linkedMapOf(
                                    ProgramScriptSlot.INITIALIZE to """int main() { zombie("WATCH-IP"); return 0; }""",
                                ),
                            ),
                        ),
                    ),
                ),
                victimId to zombieTargetState(victimId),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("watch-conn", watchHostId)
            register("victim-conn", victimId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            attackProgramRegistry = InMemoryAttackProgramRegistry(),
            programScheduler = CoroutineProgramScheduler(
                dispatcher = null,
                interestRegistry = interests,
                coroutineScope = backgroundScope,
            ),
        )

        val response = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = watchHostId,
                selector = TriggerSelector.ByIndex(0),
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "watch-zombie-fail"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertTrue(response.executed)
        assertTrue(requireNotNull(repository.load(watchHostId)).combat.activeAttacksBySourcePort.isEmpty())
        assertTrue(publisher.deltas.isEmpty())
        assertEquals(setOf("watch-conn"), publisher.uiEvents.single().first)
        assertEquals(
            "It costs \$20 to attempt an attack from a zombie port.",
            assertIs<PopupUiEvent>(publisher.uiEvents.single().second).message,
        )
    }

    @Test
    fun requestTriggerByNoteExecutesEnabledWatchAndPrefersFireSlotScript() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                targetId to watchHostState(
                    stateId = targetId,
                    watches = listOf(
                        installedWatch(
                            note = "quest-step",
                            contents = """
                                int main() {
                                    attack();
                                    return 0;
                                }
                            """.trimIndent(),
                            scriptBundle = ProgramScriptBundle(
                                family = ScriptFamily.WATCH,
                                scriptsBySlot = linkedMapOf(
                                    ProgramScriptSlot.FIRE to """
                                        int main() {
                                            logMessage("bundle");
                                            return 0;
                                        }
                                    """.trimIndent(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("source-conn", sourceId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val sink = RecordingWatchTriggerIntentSink()
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            watchExecutionCoordinator = DefaultWatchExecutionCoordinator(clock = { 0L }),
            watchTriggerIntentSink = sink,
        )

        val response = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = targetId,
                selector = TriggerSelector.ByNote("quest-step"),
                sourceIp = sourceId.value,
                triggerParameters = mapOf("mode" to StringHookValue("alpha")),
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "trigger-note"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertEquals(0, response.matchedWatchIndex)
        assertTrue(response.executed)
        assertEquals(listOf(setOf("logs")), publisher.deltas.map { it.second.deltaKeys })
        assertEquals(setOf("target-conn"), publisher.deltas.single().first)
        assertEquals("bundle", repository.load(targetId)?.logs?.entries?.single()?.renderedLine?.substringAfterLast(' '))
        assertEquals(1, sink.intents.size)
        assertEquals(TriggerSelector.ByNote("quest-step"), sink.intents.single().selector)
    }

    @Test
    fun requestTriggerMissingOrDisabledMatchesAreSuccessfulNoOps() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                targetId to watchHostState(
                    stateId = targetId,
                    watches = listOf(installedWatch(enabled = false, note = "armed")),
                ),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(repository, InMemoryInterestRegistry())

        val disabled = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = targetId,
                selector = TriggerSelector.ByIndex(0),
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            publisher = RecordingGameStatePublisher(),
        )
        val missing = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = targetId,
                selector = TriggerSelector.ByNote("missing"),
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            publisher = RecordingGameStatePublisher(),
        )

        assertTrue(disabled.accepted)
        assertEquals(0, disabled.matchedWatchIndex)
        assertFalse(disabled.executed)
        assertTrue(missing.accepted)
        assertEquals(null, missing.matchedWatchIndex)
        assertFalse(missing.executed)
        assertTrue(repository.load(targetId)?.logs?.entries?.isEmpty() == true)
    }

    @Test
    fun depositPettyCashHelperMovesMoneyOnTheInstalledBankPort() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                targetId to watchHostState(
                    stateId = targetId,
                    pettyCash = 50.0,
                    bankMoney = 10.0,
                    watches = listOf(
                        installedWatch(
                            contents = """
                                int main() {
                                    depositPettyCash(25);
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestTriggerCommand(
                stateId = sourceId,
                targetStateId = targetId,
                selector = TriggerSelector.ByIndex(0),
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "trigger-deposit"),
            publisher = publisher,
        )

        val updated = repository.load(targetId)

        requireNotNull(updated)
        assertTrue(response.accepted)
        assertEquals(0, response.matchedWatchIndex)
        assertTrue(response.executed)
        assertEquals(25.0, updated.economy.pettyCash)
        assertEquals(35.0, updated.economy.bankMoney)
        assertEquals(listOf(setOf("economy")), publisher.deltas.map { it.second.deltaKeys })
    }

    @Test
    fun requestTriggerNoteUsesTheSameExecutionPath() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                targetId to watchHostState(
                    stateId = targetId,
                    watches = listOf(
                        installedWatch(
                            note = "quest-step",
                            contents = """
                                int main() {
                                    logMessage("note");
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = RequestTriggerNoteCommand(
                stateId = sourceId,
                targetStateId = targetId,
                note = "quest-step",
                sourceIp = sourceId.value,
                triggerParameters = emptyMap(),
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "trigger-note"),
            publisher = publisher,
        )

        assertTrue(response.accepted)
        assertEquals(0, response.matchedWatchIndex)
        assertTrue(response.executed)
        assertEquals("note", repository.load(targetId)?.logs?.entries?.single()?.renderedLine?.substringAfterLast(' '))
    }

    @Test
    fun httpTriggerWatchAndNpcRemoteTriggerExecuteInline() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val localTargetId = GameStateId("WATCH-IP")
        val npcTargetId = GameStateId("NPC-IP")
        val remoteTargetId = GameStateId("REMOTE-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to sourceState(sourceId),
                localTargetId to watchHostState(
                    stateId = localTargetId,
                    websiteBody = "<html>watch</html>",
                    httpEnterScript = """
                        int main() {
                            triggerWatch(0, "mode", "alpha");
                            return 0;
                        }
                    """.trimIndent(),
                    watches = listOf(
                        installedWatch(
                            contents = """
                                int main() {
                                    logMessage(getTriggerParameter("mode"));
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
                npcTargetId to watchHostState(
                    stateId = npcTargetId,
                    isNpc = true,
                    websiteBody = "<html>npc</html>",
                    httpEnterScript = """
                        int main() {
                            triggerWatchRemote(0, "REMOTE-IP", "scope", "npc");
                            return 0;
                        }
                    """.trimIndent(),
                    watches = emptyList(),
                ),
                remoteTargetId to watchHostState(
                    stateId = remoteTargetId,
                    websiteBody = "<html>remote</html>",
                    watches = listOf(
                        installedWatch(
                            contents = """
                                int main() {
                                    logMessage(getTriggerParameter("scope"));
                                    return 0;
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("source-conn", sourceId)
            register("watch-conn", localTargetId)
            register("remote-conn", remoteTargetId)
        }
        val sink = RecordingWatchTriggerIntentSink()
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = interests,
            watchTriggerIntentSink = sink,
        )
        val runtime = HackScriptHttpHookRuntime()

        val localPublisher = RecordingGameStatePublisher()
        val localResponse = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = localTargetId,
                parameters = emptyMap(),
                httpHookRuntime = runtime,
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "hook-local"),
            publisher = localPublisher,
        )

        assertEquals("WATCH-IP", localResponse.resolvedTargetStateId.value)
        assertEquals(listOf(setOf("logs")), localPublisher.deltas.map { it.second.deltaKeys })
        assertEquals(setOf("watch-conn"), localPublisher.deltas.single().first)
        assertEquals("alpha", repository.load(localTargetId)?.logs?.entries?.single()?.renderedLine?.substringAfterLast(' '))

        val remotePublisher = RecordingGameStatePublisher()
        val remoteResponse = dispatcher.request(
            command = RequestWebpageCommand(
                sourceStateId = sourceId,
                targetStateId = npcTargetId,
                parameters = emptyMap(),
                httpHookRuntime = runtime,
            ),
            metadata = CommandMetadata(connectionId = "source-conn", requestId = "hook-remote"),
            publisher = remotePublisher,
        )

        assertEquals("NPC-IP", remoteResponse.resolvedTargetStateId.value)
        assertEquals(listOf(setOf("logs")), remotePublisher.deltas.map { it.second.deltaKeys })
        assertEquals(setOf("remote-conn"), remotePublisher.deltas.single().first)
        assertEquals("npc", repository.load(remoteTargetId)?.logs?.entries?.single()?.renderedLine?.substringAfterLast(' '))
        assertEquals(listOf(localTargetId, remoteTargetId), sink.intents.map { it.targetStateId })
    }
}

private fun sourceState(stateId: GameStateId): ComputerState {
    return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}", playerIp = stateId.value).copy(
        economy = EconomyState(
            pettyCash = 100.0,
            bankMoney = 10.0,
            defaultBankPort = 6,
        ),
        ports = listOf(
            bankingPort(),
            ftpPort(),
            httpPort(),
        ),
    )
}

private fun watchHostState(
    stateId: GameStateId,
    watches: List<InstalledWatch>,
    websiteBody: String = "<html>ok</html>",
    httpEnterScript: String = "int main() { return 0; }",
    isNpc: Boolean = false,
    pettyCash: Double = 100.0,
    bankMoney: Double = 10.0,
    additionalPorts: List<PortState> = emptyList(),
): ComputerState {
    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-${stateId.value}",
        playerIp = stateId.value,
        isNpc = isNpc,
    ).copy(
        website = WebsiteState(
            title = stateId.value,
            body = websiteBody,
        ),
        hardware = HardwareState(cpuMax = 100.0),
        economy = EconomyState(
            pettyCash = pettyCash,
            bankMoney = bankMoney,
            defaultBankPort = 6,
        ),
        ports = listOf(
            bankingPort(),
            ftpPort(),
            httpPort(
                scriptBundle = ProgramScriptBundle(
                    family = ScriptFamily.HTTP,
                    scriptsBySlot = linkedMapOf(
                        ProgramScriptSlot.ENTER to httpEnterScript,
                        ProgramScriptSlot.EXIT to "int main() { return 0; }",
                        ProgramScriptSlot.SUBMIT to "int main() { return 0; }",
                    ),
                ),
            ),
        ) + additionalPorts,
        watches = WatchManagerState(watches = watches),
    )
}

private fun zombieTargetState(stateId: GameStateId): ComputerState {
    return ComputerState.empty(
        id = stateId,
        playFabId = "PF-${stateId.value}",
        playerIp = stateId.value,
    ).copy(
        ports = listOf(
            PortState(
                number = 25,
                type = "http",
                enabled = true,
                health = 100.0,
                installedApplication = InstalledApplication(
                    name = "victim-http.bin",
                    kind = ApplicationKind.HTTP,
                    binaryPath = "/system/http.bin",
                ),
            ),
        ),
    )
}

private fun installedWatch(
    enabled: Boolean = true,
    note: String = "watch",
    installPort: Int = 6,
    contents: String = "int main() { return 0; }",
    scriptBundle: ProgramScriptBundle? = null,
): InstalledWatch {
    return InstalledWatch(
        kind = WatchKind.PETTY_CASH,
        enabled = enabled,
        note = note,
        cpuCost = 5.0,
        quantityThreshold = 0.0,
        baselineQuantity = 0.0,
        installPort = installPort,
        searchFirewallType = 0,
        observedPorts = listOf(installPort),
        contents = contents,
        scriptBundle = scriptBundle,
        compiledBinary = CompiledBinaryMetadata(
            scriptFamily = ScriptFamily.WATCH,
            applicationKind = ApplicationKind.WATCH,
            outputName = "watch.bin",
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
            binaryPath = "/system/bank.bin",
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

private fun attackPort(
    scriptBundle: ProgramScriptBundle? = null,
): PortState {
    return PortState(
        number = 12,
        type = "attack",
        enabled = true,
        defaultPort = true,
        installedApplication = InstalledApplication(
            name = "attack.bin",
            kind = ApplicationKind.ATTACK,
            binaryPath = "/system/attack.bin",
            cpuCost = 8.0,
            scriptBundle = scriptBundle,
        ),
    )
}

private class RecordingWatchTriggerIntentSink : WatchTriggerIntentSink {
    val intents = mutableListOf<WatchTriggerIntent>()

    override suspend fun emitWatchTrigger(intent: WatchTriggerIntent) {
        intents += intent
    }
}
