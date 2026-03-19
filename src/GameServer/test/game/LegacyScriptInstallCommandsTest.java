package game;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegacyScriptInstallCommandsTest {
    private final LegacyScriptInstallCommands handler = new LegacyScriptInstallCommands();

    @Test
    public void dispatch_returnsFalseForNonOwnedCommand() {
        Computer computer = baseComputer("1.1.1.1");

        boolean handled = handler.dispatch(computer, new ApplicationData("fetchports", null, 0, "source"), 0);

        assertFalse(handled);
    }

    @Test
    public void dispatch_requestInstallScript_queuesInstallScriptAndDeletesExhaustedFile() {
        Computer computer = baseComputer("2.2.2.2");
        HackerFile hackerFile = new HackerFile(HackerFile.BANKING_COMPILED);
        hackerFile.setQuantity(1);
        HashMap content = new HashMap();
        content.put("deposit", "dep");
        content.put("withdraw", "wd");
        content.put("transfer", "tr");
        hackerFile.setContent(content);
        when(computer.MyFileSystem.getFile("Public/", "bank")).thenReturn(hackerFile);

        Object[] maliciousParameters = new Object[]{"target", Float.valueOf(5.0f)};
        boolean handled = handler.dispatch(
            computer,
            new ApplicationData(
                "requestinstallscript",
                new Object[]{"9.9.9.9", Integer.valueOf(44), "Public/", "bank", maliciousParameters},
                0,
                "source"
            ),
            0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        verify(computer.MyFileSystem).deleteFile("Public/", "bank");

        ArgumentCaptor<ApplicationData> dataCaptor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computer.MyComputerHandler).addData(dataCaptor.capture(), eq("9.9.9.9"));
        ApplicationData forwarded = dataCaptor.getValue();
        assertEquals("installScript", forwarded.getFunction());
        assertEquals(44, forwarded.getPort());
        assertEquals("2.2.2.2", forwarded.getSourceIP());
        Object[] payload = (Object[]) forwarded.getParameters();
        assertSame(content, payload[0]);
        assertSame(maliciousParameters, payload[1]);
    }

    @Test
    public void dispatch_requestInstallScript_whenFileMissing_onlyMarksSystemChange() {
        Computer computer = baseComputer("3.3.3.3");
        when(computer.MyFileSystem.getFile("Public/", "missing")).thenReturn(null);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData(
                "requestinstallscript",
                new Object[]{"8.8.8.8", Integer.valueOf(12), "Public/", "missing", new Object[0]},
                0,
                "source"
            ),
            0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        verify(computer.MyComputerHandler, never()).addData(any(ApplicationData.class), any(String.class));
    }

    private Computer baseComputer(String ip) {
        Computer computer = mock(Computer.class);
        computer.ip = ip;
        computer.MyComputerHandler = mock(NetworkSwitch.class);
        computer.MyFileSystem = mock(FileSystem.class);
        return computer;
    }
}
