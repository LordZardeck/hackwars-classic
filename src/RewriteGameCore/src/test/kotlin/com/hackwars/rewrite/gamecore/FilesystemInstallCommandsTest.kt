package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilesystemInstallCommandsTest {
    @Test
    fun requestDirectorySecondaryDirectoryAndFileAreReadOnlyAndNormalizeBlankPaths() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                stateId to localState(stateId),
                targetId to targetState(targetId),
            ),
        )
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val directory = dispatcher.request(
            command = RequestDirectoryCommand(stateId = stateId, path = null),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val remoteDirectory = dispatcher.request(
            command = RequestSecondaryDirectoryCommand(
                stateId = stateId,
                targetStateId = targetId,
                path = "/Secrets",
                portNumber = 17,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val file = dispatcher.request(
            command = RequestFileCommand(
                stateId = stateId,
                path = null,
                fileName = "notes.txt",
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        assertEquals("/Public", directory.path)
        assertEquals(listOf("bank", "bank.bin", "cpu-card.bin", "notes.txt", "site", "site.bin", "wall.bin"), directory.files.map { it.name }.sorted())
        assertEquals("/Secrets", remoteDirectory.path)
        assertEquals(listOf("remote.log"), remoteDirectory.files.map { it.name })
        assertEquals("hello world", file.file?.contents)
        assertTrue(publisher.deltas.isEmpty())
    }

    @Test
    fun saveCreateDeleteAndDeleteMultiProduceFilesystemDeltasAndStateTransitions() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        dispatcher.request(
            command = CreateFolderCommand(stateId = stateId, path = "/Public", folderName = "Archive"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            command = SaveFileCommand(
                stateId = stateId,
                path = "/Public/Archive",
                file = StoredFile(
                    path = buildFilePath("/Public/Archive", "saved.txt"),
                    name = "saved.txt",
                    kind = StoredFileKind.TEXT,
                    contents = "saved payload",
                ),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            command = DeleteFileCommand(stateId = stateId, path = "/Public/Archive", fileName = "saved.txt"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        dispatcher.request(
            command = DeleteMultiCommand(
                stateId = stateId,
                path = "/Public",
                fileNames = listOf("notes.txt"),
                directoryNames = listOf("Archive"),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )

        val finalState = repository.load(stateId)

        requireNotNull(finalState)
        assertNull(finalState.filesystem.filesByPath[buildFilePath("/Public", "notes.txt")])
        assertFalse(finalState.filesystem.directoriesByPath.containsKey("/Public/Archive"))
        assertEquals(4, publisher.deltas.size)
        assertTrue(publisher.deltas.all { it.second.deltaKeys == setOf("filesystem") })
    }

    @Test
    fun compileAndDecompileMutateFilesPettyCashAndExperienceInOneTransaction() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val compile = dispatcher.request(
            command = CompileFileCommand(stateId = stateId, path = "/Public", fileName = "bank"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val decompile = dispatcher.request(
            command = DecompileFileCommand(stateId = stateId, path = "/Public", fileName = "bank.bin"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val finalState = repository.load(stateId)

        requireNotNull(finalState)
        assertEquals("bank.bin", compile.compiledFile.name)
        assertEquals(425.0, compile.pettyCashAfter)
        assertEquals(4, compile.experienceAfter)
        assertEquals("bank", decompile.decompiledFile.name)
        assertEquals(500.0, decompile.pettyCashAfter)
        assertEquals(0, decompile.experienceAfter)
        assertEquals(listOf(setOf("filesystem", "economy", "stats"), setOf("filesystem", "economy", "stats")), publisher.deltas.map { it.second.deltaKeys })
        assertNotNull(finalState.filesystem.filesByPath[buildFilePath("/Public", "bank")])
    }

    @Test
    fun httpScriptsRoundTripAcrossSaveRequestCompileDecompileAndInstall() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)
        val bundle = httpScriptBundle()

        dispatcher.request(
            command = SaveFileCommand(
                stateId = stateId,
                path = "/Public",
                file = StoredFile(
                    path = buildFilePath("/Public", "portal"),
                    name = "portal",
                    kind = StoredFileKind.SCRIPT_SOURCE,
                    contents = "legacy-http-script",
                    compileCost = 40.0,
                    compiledBinary = CompiledBinaryMetadata(
                        scriptFamily = ScriptFamily.HTTP,
                        outputName = "portal.bin",
                        applicationKind = ApplicationKind.HTTP,
                        experienceAward = 5,
                    ),
                    scriptBundle = bundle,
                ),
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val requested = dispatcher.request(
            command = RequestFileCommand(stateId = stateId, path = "/Public", fileName = "portal"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val compile = dispatcher.request(
            command = CompileFileCommand(stateId = stateId, path = "/Public", fileName = "portal"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val decompile = dispatcher.request(
            command = DecompileFileCommand(stateId = stateId, path = "/Public", fileName = "portal.bin"),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val install = dispatcher.request(
            command = InstallApplicationCommand(
                stateId = stateId,
                path = "/Public",
                fileName = "site.bin",
                portNumber = 80,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val state = repository.load(stateId)

        requireNotNull(state)
        assertEquals(bundle, requested.file?.scriptBundle)
        assertEquals(bundle, compile.compiledFile.scriptBundle)
        assertEquals(bundle, decompile.decompiledFile.scriptBundle)
        assertEquals(bundle, install.installedApplication.scriptBundle)
        assertEquals(bundle, state.ports.single { it.number == 80 }.installedApplication?.scriptBundle)
    }

    @Test
    fun installApplicationConsumesBinaryCreatesPortAndSetsDefaultBank() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = InstallApplicationCommand(
                stateId = stateId,
                path = "/Public",
                fileName = "bank.bin",
                portNumber = 6,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val state = repository.load(stateId)

        requireNotNull(state)
        assertEquals(6, response.defaultBankPort)
        assertEquals(6, state.economy.defaultBankPort)
        assertEquals("bank.bin", state.ports.single { it.number == 6 }.installedApplication?.name)
        assertNull(state.filesystem.filesByPath[buildFilePath("/Public", "bank.bin")])
        assertEquals(setOf("filesystem", "ports", "economy"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun installFirewallReturnsReplacedFirewallToDiskAndUpdatesPort() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = InstallFirewallCommand(
                stateId = stateId,
                path = "/Public",
                fileName = "wall.bin",
                portNumber = 19,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
            publisher = publisher,
        )
        val state = repository.load(stateId)

        requireNotNull(state)
        assertEquals("wall.bin", response.installedFirewall.name)
        assertEquals("/firewalls/OldWall.bin", response.returnedFirewall?.path)
        assertNotNull(state.filesystem.filesByPath["/firewalls/OldWall.bin"])
        assertEquals("wall.bin", state.ports.single { it.number == 19 }.installedFirewall?.name)
        assertEquals(setOf("filesystem", "ports"), publisher.deltas.single().second.deltaKeys)
    }

    @Test
    fun installEquipmentMutatesSlotAndRejectsBadSlotFileCombinations() = runTest {
        val stateId = GameStateId("LOCAL-IP")
        val repository = InMemoryComputerStateRepository(seededStates = mapOf(stateId to localState(stateId)))
        val interests = InMemoryInterestRegistry()
        interests.register("conn-1", stateId)
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = InstallEquipmentCommand(
                stateId = stateId,
                path = "/Public",
                fileName = "cpu-card.bin",
                slot = EquipmentSlot.CPU,
            ),
            metadata = CommandMetadata(connectionId = "conn-1"),
        )
        val state = repository.load(stateId)

        requireNotNull(state)
        assertEquals(EquipmentSlot.CPU, response.slot)
        assertEquals("cpu-card.bin", state.hardware.equipmentSlots[EquipmentSlot.CPU]?.name)

        assertFailsWith<IllegalArgumentException> {
            dispatcher.request(
                command = InstallEquipmentCommand(
                    stateId = stateId,
                    path = "/Public",
                    fileName = "cpu-card.bin",
                    slot = EquipmentSlot.MEMORY,
                ),
                metadata = CommandMetadata(connectionId = "conn-1"),
            )
        }
    }

    private fun localState(stateId: GameStateId): ComputerState {
        var filesystem = ComputerState.empty(
            id = stateId,
            playFabId = "PF-LOCALUSER",
        ).filesystem
            .withCurrentPath("/Public")
            .ensureDirectory("/Public")
            .ensureDirectory("/Docs")
        filesystem = filesystem
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "notes.txt"),
                    name = "notes.txt",
                    kind = StoredFileKind.TEXT,
                    contents = "hello world",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "bank"),
                    name = "bank",
                    kind = StoredFileKind.SCRIPT_SOURCE,
                    contents = "bank source",
                    compileCost = 75.0,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.BANKING,
                        bankingApplication = true,
                        outputName = "bank.bin",
                        experienceAward = 4,
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "bank.bin"),
                    name = "bank.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "bank source",
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.BANKING,
                        bankingApplication = true,
                        outputName = "bank.bin",
                        experienceAward = 4,
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "site"),
                    name = "site",
                    kind = StoredFileKind.SCRIPT_SOURCE,
                    contents = "http source",
                    compileCost = 40.0,
                    compiledBinary = CompiledBinaryMetadata(
                        scriptFamily = ScriptFamily.HTTP,
                        outputName = "site.bin",
                        applicationKind = ApplicationKind.HTTP,
                        experienceAward = 5,
                    ),
                    scriptBundle = httpScriptBundle(),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "site.bin"),
                    name = "site.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "http source",
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        scriptFamily = ScriptFamily.HTTP,
                        outputName = "site.bin",
                        applicationKind = ApplicationKind.HTTP,
                        experienceAward = 5,
                    ),
                    scriptBundle = httpScriptBundle(),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "wall.bin"),
                    name = "wall.bin",
                    kind = StoredFileKind.FIREWALL_BINARY,
                    contents = "firewall source",
                    compiledBinary = CompiledBinaryMetadata(
                        firewallKind = FirewallKind.CUSTOM,
                        outputName = "wall.bin",
                        strength = 9,
                    ),
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "cpu-card.bin"),
                    name = "cpu-card.bin",
                    kind = StoredFileKind.EQUIPMENT_BINARY,
                    contents = "cpu card",
                    compiledBinary = CompiledBinaryMetadata(
                        equipmentSlot = EquipmentSlot.CPU,
                        outputName = "cpu-card.bin",
                    ),
                ),
            )

        return ComputerState.empty(
            id = stateId,
            playFabId = "PF-LOCALUSER",
        ).copy(
            economy = ComputerState.empty(id = stateId).economy.copy(pettyCash = 500.0),
            filesystem = filesystem,
            ports = listOf(
                PortState(number = 19, type = "ssh", installedFirewall = InstalledFirewall(name = "OldWall")),
                PortState(number = 22, type = "ssh"),
                PortState(number = 80, type = "http"),
                PortState(number = 443, type = "https"),
            ),
        )
    }

    private fun targetState(stateId: GameStateId): ComputerState {
        var filesystem = ComputerState.empty(id = stateId, playerIp = stateId.value).filesystem
            .withCurrentPath("/Secrets")
            .ensureDirectory("/Secrets")
        filesystem = filesystem.saveFile(
            StoredFile(
                path = buildFilePath("/Secrets", "remote.log"),
                name = "remote.log",
                kind = StoredFileKind.TEXT,
                contents = "remote data",
            ),
        )
        return ComputerState.empty(id = stateId, playerIp = stateId.value).copy(
            filesystem = filesystem,
            ports = listOf(
                PortState(number = 22, type = "ssh"),
                PortState(number = 80, type = "http"),
                PortState(number = 443, type = "https"),
            ),
        )
    }

    private fun httpScriptBundle(): ProgramScriptBundle {
        return ProgramScriptBundle(
            family = ScriptFamily.HTTP,
            scriptsBySlot = linkedMapOf(
                ProgramScriptSlot.ENTER to "int main() { replaceContent(\"first\", getVisitorIP()); return 0; }",
                ProgramScriptSlot.EXIT to "int main() { return 0; }",
                ProgramScriptSlot.SUBMIT to "int main() { hideStore(); return 0; }",
            ),
        )
    }
}
