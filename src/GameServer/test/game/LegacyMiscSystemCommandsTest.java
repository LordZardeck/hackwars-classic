package game;

import assignments.PacketAssignment;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyMiscSystemCommandsTest {
    private final LegacyMiscSystemCommands handler = new LegacyMiscSystemCommands();

    @Test
    public void dispatch_returnsFalse_forNonOwnedCommand() {
        Computer computer = baseComputer();

        boolean handled = handler.dispatch(computer, new ApplicationData("requestdirectory", null, 0, "10.0.0.1"), 0);

        assertFalse(handled);
    }

    @Test
    public void dispatch_bountyhttp_updatesLastBountyHttp() {
        Computer computer = baseComputer();

        boolean handled = handler.dispatch(computer, new ApplicationData("bountyhttp", "12.13.14.15", 0, "10.0.0.1"), 0);

        assertTrue(handled);
        assertEquals("12.13.14.15", computer.getLastBountyHTTPIP());
    }

    @Test
    public void dispatch_unlock_withMatchingCode_clearsLockedState() {
        Computer computer = baseComputer();
        computer.unlockKey = "alpha";
        computer.locked = true;
        computer.lockCount = 42;
        computer.RESEND_CAPTCHA = false;

        boolean handled = handler.dispatch(computer, new ApplicationData("unlock", "alpha", 0, "10.0.0.1"), 0);

        assertTrue(handled);
        assertFalse(computer.locked);
        assertEquals(0, computer.lockCount);
        assertFalse(computer.RESEND_CAPTCHA);
    }

    @Test
    public void dispatch_unlock_withWrongCode_requestsCaptchaResend() {
        Computer computer = baseComputer();
        computer.unlockKey = "alpha";
        computer.locked = true;
        computer.lockCount = 42;
        computer.RESEND_CAPTCHA = false;

        boolean handled = handler.dispatch(computer, new ApplicationData("unlock", "beta", 0, "10.0.0.1"), 0);

        assertTrue(handled);
        assertTrue(computer.locked);
        assertEquals(42, computer.lockCount);
        assertTrue(computer.RESEND_CAPTCHA);
    }

    @Test
    public void dispatch_ping_isHandledWithoutMutatingPingTimestamp() {
        Computer computer = baseComputer();
        computer.lastPingTime = 777L;

        boolean handled = handler.dispatch(computer, new ApplicationData("ping", null, 0, "10.0.0.1"), 0);

        assertTrue(handled);
        assertEquals(777L, computer.lastPingTime);
    }

    @Test
    public void dispatch_hacktendoCommands_areHandledAsNoOps() {
        Computer computer = baseComputer();

        boolean targetHandled = handler.dispatch(
                computer,
                new ApplicationData("hacktendoTarget", new Object[]{1, 2, 3, 4}, 0, "10.0.0.1"),
                0
        );
        boolean activateHandled = handler.dispatch(
                computer,
                new ApplicationData("hacktendoActivate", new Object[]{5, 6}, 0, "10.0.0.1"),
                0
        );
        boolean clueHandled = handler.dispatch(
                computer,
                new ApplicationData("cluedata", "payload", 0, "10.0.0.1"),
                0
        );

        assertTrue(targetHandled);
        assertTrue(activateHandled);
        assertTrue(clueHandled);
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
}
