package game

import com.hackwars.rpc.FetchPorts
import game.payload.InstallScriptPayload
import game.payload.RequestInstallScriptPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.HashMap

class LegacyScriptInstallCommandsTest {
    private val handler = LegacyScriptInstallCommands()

    @Test
    fun dispatch_returnsFalseForNonOwnedCommand() {
        val fixture = baseComputer("1.1.1.1")

        val handled = handler.dispatch(fixture.computer, ApplicationData(FetchPorts("source"), 0, "source"), 0)

        assertFalse(handled)
    }

    @Test
    fun dispatch_requestInstallScript_queuesInstallScriptAndDeletesExhaustedFile() {
        val fixture = baseComputer("2.2.2.2")
        val hackerFile = HackerFile(HackerFile.BANKING_COMPILED)
        hackerFile.setQuantity(1)
        val content = HashMap<Any?, Any?>()
        content["deposit"] = "dep"
        content["withdraw"] = "wd"
        content["transfer"] = "tr"
        hackerFile.setContent(content)
        whenever(fixture.fileSystem.getFile("Public/", "bank")).thenReturn(hackerFile)

        val maliciousParameters = arrayOf<Any>("target", 5.0f)
        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(RequestInstallScriptPayload("9.9.9.9", 44, "Public/", "bank", maliciousParameters), 0, "source"),
            0
        )

        assertTrue(handled)
        assertTrue(fixture.computer.systemChange)
        verify(fixture.fileSystem).deleteFile("Public/", "bank")

        val dataCaptor = argumentCaptor<ApplicationData>()
        val ipCaptor = argumentCaptor<String>()
        verify(fixture.networkSwitch).addData(dataCaptor.capture(), ipCaptor.capture())
        assertEquals("9.9.9.9", ipCaptor.firstValue)
        val forwarded = dataCaptor.firstValue
        assertEquals("installScript", forwarded.command.wireName())
        assertEquals(44, forwarded.port)
        assertEquals("2.2.2.2", forwarded.sourceIP)
        val payload = forwarded.payload as InstallScriptPayload
        assertSame(content, payload.script)
        assertSame(maliciousParameters, payload.maliciousParameters)
    }

    @Test
    fun dispatch_requestInstallScript_whenFileMissing_onlyMarksSystemChange() {
        val fixture = baseComputer("3.3.3.3")
        whenever(fixture.fileSystem.getFile("Public/", "missing")).thenReturn(null)

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(RequestInstallScriptPayload("8.8.8.8", 12, "Public/", "missing", emptyArray<Any?>()), 0, "source"),
            0
        )

        assertTrue(handled)
        assertTrue(fixture.computer.systemChange)
        verifyNoInteractions(fixture.networkSwitch)
    }

    private data class ComputerFixture(
        val computer: Computer,
        val networkSwitch: NetworkSwitch,
        val fileSystem: FileSystem
    )

    private fun baseComputer(ip: String): ComputerFixture {
        val computer = allocateComputer()
        computer.ip = ip
        computer.connectionID = -1
        val networkSwitch = mock<NetworkSwitch>()
        val fileSystem = mock<FileSystem>()
        computer.MyComputerHandler = networkSwitch
        computer.MyFileSystem = fileSystem
        return ComputerFixture(computer, networkSwitch, fileSystem)
    }

    private fun allocateComputer(): Computer {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(Computer::class.java) as Computer
    }
}
