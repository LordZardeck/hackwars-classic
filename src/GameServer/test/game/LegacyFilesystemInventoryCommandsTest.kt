package game

import assignments.PacketAssignment
import com.hackwars.rpc.DeleteFile
import com.hackwars.rpc.RequestAttack
import com.hackwars.rpc.RequestFile
import com.hackwars.rpc.RequestGame
import com.hackwars.rpc.SetFileDescription
import hackscript.model.TypeBoolean
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import game.payload.DeliveredDirectoryToNpcPayload
import game.payload.RequestDirectoryPayload
import game.payload.SaveFileRequestPayload
import game.payload.StolenSaveFilePayload
import java.util.ArrayList
import java.util.HashMap

class LegacyFilesystemInventoryCommandsTest {
    private val handler = LegacyFilesystemInventoryCommands()

    @Test
    fun dispatch_returnsFalse_forNonOwnedCommand() {
        val computer = baseComputer()

        val handled = handler.dispatch(computer, ApplicationData(RequestAttack("10.0.0.2", 0, "10.0.0.1", 0, null, null, null, null), 0, "10.0.0.1"), 0)

        assertFalse(handled)
    }

    @Test
    fun dispatch_requestdirectory_setsPacketDirectory() {
        val computer = baseComputer()
        val file = textFile("notes.txt")
        computer.MyFileSystem.addFile(file, true)

        val handled = handler.dispatch(
            computer,
            ApplicationData(RequestDirectoryPayload("", 17), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertTrue(computer.systemChange)
        val directory = computer.PA.getDirectory()
        assertNotNull(directory)
        assertEquals(Integer.valueOf(17), directory[0])
        assertEquals("notes.txt", (directory[1] as Array<*>)[0])
    }

    @Test
    fun dispatch_requestfile_clonesGameFiles_withoutContent() {
        val computer = baseComputer()
        val file = gameFile("arcade")
        computer.MyFileSystem.addFile(file, true)

        val handled = handler.dispatch(
            computer,
            ApplicationData(RequestFile("10.0.0.1", "", "arcade"), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertNotNull(computer.PA.getFile())
        assertNull(computer.PA.getFile().getContent())
        assertNotNull(file.getContent())
    }

    @Test
    fun dispatch_requestgame_loadsSaveVariables() {
        val computer = baseComputer()
        val gameFile = gameFile("adventure")
        computer.MyFileSystem.addFile(gameFile, true)

        val saveFile = textFile("adventure.save")
        val saveContent = HashMap<Any?, Any?>()
        saveContent["data"] = "name\tstring\tplayer\nalive\tbool\ttrue\nscore\tint\t7"
        saveContent["level"] = "1"
        saveFile.setContent(saveContent)
        computer.MyFileSystem.addFile(saveFile, true)

        val handled = handler.dispatch(
            computer,
            ApplicationData(RequestGame("10.0.0.1", "", "adventure"), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        val loadFile = computer.PA.getLoadFile()
        assertEquals("player", (loadFile["name"] as TypeString).stringValue)
        assertTrue((loadFile["alive"] as TypeBoolean).booleanValue)
        assertEquals(7, (loadFile["score"] as TypeInteger).intValue)
        assertEquals(gameFile, computer.PA.getFile())
    }

    @Test
    fun dispatch_deletefile_removesFile_andRequestsPrimaryRefresh() {
        val computer = baseComputer()
        val file = textFile("trash.txt")
        computer.MyFileSystem.addFile(file, true)

        val handled = handler.dispatch(
            computer,
            ApplicationData(DeleteFile("10.0.0.1", "", "trash.txt"), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertNull(computer.MyFileSystem.getFile("", "trash.txt"))
        assertTrue(computer.PA.requestPrimary())
        assertEquals(1, computer.PA.getRequestPrimaryID())
    }

    @Test
    fun dispatch_savefile_usesComputerSaveHelper() {
        val computer = baseComputer()
        val file = textFile("draft.txt")

        val handled = handler.dispatch(
            computer,
            ApplicationData(SaveFileRequestPayload("", file), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertNotNull(computer.MyFileSystem.getFile("", "draft.txt"))
        assertTrue(computer.PA.requestPrimary())
        assertEquals(1, computer.PA.getRequestPrimaryID())
    }

    @Test
    fun dispatch_delivereddirectory_updatesSecondaryDirectory_andNpcAccessFlag() {
        val computer = baseComputer()
        val delivered = arrayOf<Any?>("remote-file")

        val handled = handler.dispatch(
            computer,
            ApplicationData(DeliveredDirectoryToNpcPayload(delivered, true), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertEquals(delivered, computer.PA.getSecondaryDirectory())
        assertFalse(computer.PA.getAllowedDir())
        assertTrue(computer.systemChange)
    }

    @Test
    fun dispatch_setfiledescription_ignoresClueFiles() {
        val computer = baseComputer()
        val clue = clueFile("clue.txt", "original")
        computer.MyFileSystem.addFile(clue, true)

        val handled = handler.dispatch(
            computer,
            ApplicationData(SetFileDescription("10.0.0.1", "", "clue.txt", "updated"), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertEquals("original", clue.description)
        assertNull(computer.PA.getFile())
    }

    @Test
    fun dispatch_savefile_withStolenMetadata_addsAttackAndGameMessages() {
        val computer = baseComputer()
        computer.connectionID = 1
        val file = textFile("stolen.txt")

        val handled = handler.dispatch(
            computer,
            ApplicationData(StolenSaveFilePayload("", file, "9.9.9.9", 44), 0, "10.0.0.1"),
            0
        )

        assertTrue(handled)
        assertEquals(2, computer.Messages.size)
        val attackMessage = computer.Messages[0] as Array<*>
        val gameMessage = computer.Messages[1] as Array<*>
        assertTrue((attackMessage[0] as String).contains("stolen.txt"))
        assertTrue((attackMessage[0] as String).contains("successfully stolen"))
        assertEquals(Integer.valueOf(44), (attackMessage[3] as Array<*>)[0])
        assertEquals("9.9.9.9", (attackMessage[3] as Array<*>)[1])
        assertTrue((gameMessage[0] as String).contains("from 9.9.9.9"))
    }

    private fun baseComputer(): Computer {
        val computer = Mockito.mock(Computer::class.java, Answers.CALLS_REAL_METHODS)
        computer.ip = "10.0.0.1"
        computer.userName = "tester"
        computer.connectionID = -1
        computer.systemChange = false
        computer.Messages = ArrayList()
        computer.Damage = ArrayList()
        computer.CurrentQuests = HashMap()
        computer.Stats = HashMap()
        computer.PA = PacketAssignment(0)
        computer.equipmentSheet = Mockito.mock(EquipmentSheet::class.java)
        computer.messageHandler = MessageHandler(computer)
        computer.MyFileSystem = FileSystem(computer)
        return computer
    }

    private fun textFile(name: String): HackerFile {
        val file = HackerFile(HackerFile.TEXT)
        file.setName(name)
        file.setLocation("")
        file.setDescription("text")
        file.setQuantity(1)
        val content = HashMap<Any?, Any?>()
        content["data"] = "hello"
        content["level"] = "1"
        file.setContent(content)
        return file
    }

    private fun gameFile(name: String): HackerFile {
        val file = HackerFile(HackerFile.GAME)
        file.setName(name)
        file.setLocation("")
        file.setDescription("game")
        file.setQuantity(1)
        val content = HashMap<Any?, Any?>()
        content["data"] = "payload"
        content["level"] = "1"
        file.setContent(content)
        return file
    }

    private fun clueFile(name: String, description: String): HackerFile {
        val file = HackerFile(HackerFile.CLUE)
        file.setName(name)
        file.setLocation("")
        file.setDescription(description)
        file.setQuantity(1)
        val content = HashMap<Any?, Any?>()
        content["currentstep"] = "1"
        content["cluelevel"] = "1"
        content["step0"] = "a"
        content["step1"] = "b"
        content["step2"] = "c"
        content["step3"] = "d"
        content["step4"] = "e"
        content["step5"] = "f"
        file.setContent(content)
        return file
    }
}
