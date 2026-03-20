package game

import com.hackwars.game.program.FTPProgram
import game.payload.DeliveredDirectoryToPlayerPayload
import game.payload.RequestSecondaryDirectoryPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor

class FTPProgramTypedPayloadTest {
    @Test
    fun execute_requestSecondaryDirectory_emitsTypedDirectoryPayload() {
        val computer = mock(Computer::class.java)
        val computerHandler = mock(NetworkSwitch::class.java)
        val fileSystem = mock(FileSystem::class.java)
        val parentPort = mock(Port::class.java)
        val program = FTPProgram(computer, computerHandler, fileSystem, parentPort)

        `when`(computer.getType()).thenReturn(Port.BANKING)
        `when`(computer.getIP()).thenReturn("10.0.0.1")
        `when`(fileSystem.getDirectory("Public/")).thenReturn(arrayOf<Any?>("file-1", "file-2"))

        program.execute(ApplicationData(RequestSecondaryDirectoryPayload("9.9.9.9", "Public/", 7), 0, "source-ip"))

        val dataCaptor = argumentCaptor<ApplicationData>()
        val targetCaptor = argumentCaptor<String>()
        verify(computerHandler).addData(dataCaptor.capture(), targetCaptor.capture())

        assertEquals("9.9.9.9", targetCaptor.firstValue)
        assertEquals("delivereddirectory", dataCaptor.firstValue.command.wireName())
        val payload = dataCaptor.firstValue.payload as DeliveredDirectoryToPlayerPayload
        assertEquals(3, payload.directory.size)
        assertEquals(7, payload.directory[0])
        assertTrue(payload.directory[1] is String)
        assertTrue(payload.directory[2] is String)
    }
}
