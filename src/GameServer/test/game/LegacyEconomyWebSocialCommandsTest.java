package game;

import assignments.PacketAssignment;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.mockito.Answers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LegacyEconomyWebSocialCommandsTest {
    private final LegacyEconomyWebSocialCommands handler = new LegacyEconomyWebSocialCommands();

    @Test
    public void dispatchReturnsFalseForUnhandledCommand() {
        Computer computer = mock(Computer.class);

        boolean handled = handler.dispatch(computer, new ApplicationData("unhandled", null, 0, "10.0.0.1"), 0);

        assertFalse(handled);
    }

    @Test
    public void setPreferencesStoresProvidedPreferences() {
        Computer computer = mock(Computer.class);
        HashMap preferences = new HashMap();
        preferences.put("music", Boolean.TRUE);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("setpreferences", new Object[]{"ignored", preferences}, 0, "10.0.0.1"),
            0
        );

        assertTrue(handled);
        assertSame(preferences, computer.preferences);
    }

    @Test
    public void requestPageCopiesSavedContentIntoThePacket() {
        Computer computer = mock(Computer.class, Answers.CALLS_REAL_METHODS);
        PacketAssignment packetAssignment = new PacketAssignment(0);
        computer.PA = packetAssignment;
        computer.pageTitle = "Welcome";
        computer.pageBody = "Hello";

        boolean handled = handler.dispatch(computer, new ApplicationData("requestpage", null, 0, "10.0.0.1"), 0);

        assertTrue(handled);
        assertTrue(computer.systemChange);
        org.junit.Assert.assertEquals("Welcome", packetAssignment.getTitle());
        org.junit.Assert.assertEquals("Hello", packetAssignment.getBody());
    }

    @Test
    public void savePageRejectsOversizedBody() {
        Computer computer = mock(Computer.class);
        StringBuilder builder = new StringBuilder(30001);
        for (int i = 0; i < 30001; i++) {
            builder.append('a');
        }

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("savepage", new Object[]{"Title", builder.toString()}, 0, "10.0.0.1"),
            0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        verify(computer).addMessage(MessageHandler.WEBSITE_SAVE_FAIL_TOO_BIG);
    }
}
