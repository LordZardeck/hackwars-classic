package game;

import assignments.PacketAssignment;
import hackscript.model.TypeBoolean;
import hackscript.model.TypeInteger;
import hackscript.model.TypeString;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegacyFilesystemInventoryCommandsTest {
    private final LegacyFilesystemInventoryCommands handler = new LegacyFilesystemInventoryCommands();

    @Test
    public void dispatch_returnsFalse_forNonOwnedCommand() {
        Computer computer = baseComputer();

        boolean handled = handler.dispatch(computer, new ApplicationData("requestwebpage", null, 0, "10.0.0.1"), 0);

        assertFalse(handled);
    }

    @Test
    public void dispatch_requestdirectory_setsPacketDirectory() {
        Computer computer = baseComputer();
        HackerFile file = textFile("notes.txt");
        computer.MyFileSystem.addFile(file, true);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("requestdirectory", new Object[]{"", Integer.valueOf(17)}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        Object[] directory = computer.PA.getDirectory();
        assertNotNull(directory);
        assertEquals(Integer.valueOf(17), directory[0]);
        assertEquals("notes.txt", ((Object[]) directory[1])[0]);
    }

    @Test
    public void dispatch_requestfile_clonesGameFiles_withoutContent() {
        Computer computer = baseComputer();
        HackerFile file = gameFile("arcade");
        computer.MyFileSystem.addFile(file, true);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("requestfile", new String[]{"", "arcade"}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertNotNull(computer.PA.getFile());
        assertNull(computer.PA.getFile().getContent());
        assertNotNull(file.getContent());
    }

    @Test
    public void dispatch_requestgame_loadsSaveVariables() {
        Computer computer = baseComputer();
        HackerFile gameFile = gameFile("adventure");
        computer.MyFileSystem.addFile(gameFile, true);

        HackerFile saveFile = textFile("adventure.save");
        HashMap saveContent = new HashMap();
        saveContent.put("data", "name\tstring\tplayer\nalive\tbool\ttrue\nscore\tint\t7");
        saveContent.put("level", "1");
        saveFile.setContent(saveContent);
        computer.MyFileSystem.addFile(saveFile, true);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("requestgame", new String[]{"", "adventure"}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        HashMap loadFile = computer.PA.getLoadFile();
        assertEquals("player", ((TypeString) loadFile.get("name")).getStringValue());
        assertTrue(((TypeBoolean) loadFile.get("alive")).getBooleanValue());
        assertEquals(7, ((TypeInteger) loadFile.get("score")).getIntValue());
        assertEquals(gameFile, computer.PA.getFile());
    }

    @Test
    public void dispatch_deletefile_removesFile_andRequestsPrimaryRefresh() {
        Computer computer = baseComputer();
        HackerFile file = textFile("trash.txt");
        computer.MyFileSystem.addFile(file, true);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("deletefile", new Object[]{"", "trash.txt"}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertNull(computer.MyFileSystem.getFile("", "trash.txt"));
        assertTrue(computer.PA.requestPrimary());
        assertEquals(1, computer.PA.getRequestPrimaryID());
    }

    @Test
    public void dispatch_savefile_usesComputerSaveHelper() {
        Computer computer = baseComputer();
        HackerFile file = textFile("draft.txt");

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("savefile", new Object[]{"", file}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertNotNull(computer.MyFileSystem.getFile("", "draft.txt"));
        assertTrue(computer.PA.requestPrimary());
        assertEquals(1, computer.PA.getRequestPrimaryID());
    }

    @Test
    public void dispatch_delivereddirectory_updatesSecondaryDirectory_andNpcAccessFlag() {
        Computer computer = baseComputer();
        Object[] delivered = new Object[]{"remote-file"};

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("delivereddirectory", new Object[]{delivered, Boolean.TRUE}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(delivered, computer.PA.getSecondaryDirectory());
        assertFalse(computer.PA.getAllowedDir());
        assertTrue(computer.systemChange);
    }

    @Test
    public void dispatch_setfiledescription_ignoresClueFiles() {
        Computer computer = baseComputer();
        HackerFile clue = clueFile("clue.txt", "original");
        computer.MyFileSystem.addFile(clue, true);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("setfiledescription", new Object[]{"", "clue.txt", "updated"}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals("original", clue.getDescription());
        assertNull(computer.PA.getFile());
    }

    @Test
    public void dispatch_savefile_withStolenMetadata_addsAttackAndGameMessages() {
        Computer computer = baseComputer();
        computer.connectionID = 1;
        HackerFile file = textFile("stolen.txt");

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("savefile", new Object[]{"", file, "9.9.9.9", Integer.valueOf(44)}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(2, computer.Messages.size());
        Object[] attackMessage = (Object[]) computer.Messages.get(0);
        Object[] gameMessage = (Object[]) computer.Messages.get(1);
        assertTrue(((String) attackMessage[0]).contains("stolen.txt"));
        assertTrue(((String) attackMessage[0]).contains("successfully stolen"));
        assertEquals(Integer.valueOf(44), ((Object[]) attackMessage[3])[0]);
        assertEquals("9.9.9.9", ((Object[]) attackMessage[3])[1]);
        assertTrue(((String) gameMessage[0]).contains("from 9.9.9.9"));
    }

    private Computer baseComputer() {
        Computer computer = Mockito.mock(Computer.class, Answers.CALLS_REAL_METHODS);
        computer.ip = "10.0.0.1";
        computer.userName = "tester";
        computer.connectionID = -1;
        computer.systemChange = false;
        computer.Messages = new ArrayList();
        computer.Damage = new ArrayList();
        computer.CurrentQuests = new HashMap();
        computer.Stats = new HashMap();
        computer.PA = new PacketAssignment(0);
        computer.MyEquipmentSheet = Mockito.mock(EquipmentSheet.class);
        computer.messageHandler = new MessageHandler(computer);
        computer.MyFileSystem = new FileSystem(computer);
        return computer;
    }

    private HackerFile textFile(String name) {
        HackerFile file = new HackerFile(HackerFile.TEXT);
        file.setName(name);
        file.setLocation("");
        file.setDescription("text");
        file.setQuantity(1);
        HashMap content = new HashMap();
        content.put("data", "hello");
        content.put("level", "1");
        file.setContent(content);
        return file;
    }

    private HackerFile gameFile(String name) {
        HackerFile file = new HackerFile(HackerFile.GAME);
        file.setName(name);
        file.setLocation("");
        file.setDescription("game");
        file.setQuantity(1);
        HashMap content = new HashMap();
        content.put("data", "payload");
        content.put("level", "1");
        file.setContent(content);
        return file;
    }

    private HackerFile clueFile(String name, String description) {
        HackerFile file = new HackerFile(HackerFile.CLUE);
        file.setName(name);
        file.setLocation("");
        file.setDescription(description);
        file.setQuantity(1);
        HashMap content = new HashMap();
        content.put("currentstep", "1");
        content.put("cluelevel", "1");
        content.put("step0", "a");
        content.put("step1", "b");
        content.put("step2", "c");
        content.put("step3", "d");
        content.put("step4", "e");
        content.put("step5", "f");
        file.setContent(content);
        return file;
    }
}
