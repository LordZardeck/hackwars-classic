package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.FileCompiledEvent
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.buildFilePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComputerStateSerializerTest {
    private val serializer = ComputerStateSerializer()

    @Test
    fun roundTripsTypedComputerStateToUtf8JsonBytes() {
        val state = ComputerState.empty(
            id = GameStateId("LOCAL-IP"),
            playFabId = "PF-LOCALUSER",
        ).copy(
            ports = listOf(
                PortState(number = 22, type = "ssh"),
                PortState(
                    number = 80,
                    type = "banking",
                    installedApplication = InstalledApplication(name = "bank.bin"),
                ),
            ),
        )

        val reloaded = serializer.decodeState(serializer.encodeState(state))

        assertEquals(state, reloaded)
    }

    @Test
    fun roundTripsTypedComputerEventsToUtf8JsonBytes() {
        val event = FileCompiledEvent(
            sourceFilePath = buildFilePath("/Public", "bank.hws"),
            remainingSourceFile = StoredFile(
                path = buildFilePath("/Public", "bank.hws"),
                name = "bank.hws",
                kind = StoredFileKind.SCRIPT_SOURCE,
                contents = "bank script",
                compileCost = 75.0,
                compiledBinary = CompiledBinaryMetadata(
                    scriptFamily = ScriptFamily.BANKING,
                    outputName = "bank.bin",
                ),
            ),
            compiledFile = StoredFile(
                path = buildFilePath("/Public", "bank.bin"),
                name = "bank.bin",
                kind = StoredFileKind.APPLICATION_BINARY,
                contents = "bank script",
                compileCost = 75.0,
                compiledBinary = CompiledBinaryMetadata(
                    scriptFamily = ScriptFamily.BANKING,
                    outputName = "bank.bin",
                ),
            ),
            pettyCashDelta = -75.0,
            scriptFamily = ScriptFamily.BANKING,
            experienceDelta = 3,
        )

        val reloaded = serializer.decodeEvent(serializer.encodeEvent(event))

        assertIs<FileCompiledEvent>(reloaded)
        assertEquals(event, reloaded)
    }
}
