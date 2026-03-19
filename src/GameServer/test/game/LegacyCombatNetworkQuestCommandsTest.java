package game;

import assignments.PacketPort;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegacyCombatNetworkQuestCommandsTest {
    private final LegacyCombatNetworkQuestCommands handler = new LegacyCombatNetworkQuestCommands();

    @Test
    public void dispatch_returnsFalseForUnhandledCommand() {
        Computer computer = mock(Computer.class);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("not-owned", null, 0, "10.0.0.1"),
                0
        );

        assertFalse(handled);
    }

    @Test
    public void dispatch_giveExperienceUpdatesAttackStatAndHealthFlag() {
        Computer computer = mock(Computer.class);
        computer.Stats = new HashMap();
        computer.Stats.put("Attack", 300.0f);
        computer.healthChange = false;

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("giveexperience", new Object[]{"attack", 25.0f}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(325.0f, (Float) computer.Stats.get("Attack"), 0.001f);
        assertTrue(computer.healthChange);
    }

    @Test
    public void dispatch_giveTaskCreatesQuestEntryWhenQuestIsActive() {
        Computer computer = mock(Computer.class);
        computer.CurrentQuests = new HashMap();
        computer.CompletedQuests = new ArrayList();
        when(computer.checkQuest(42)).thenReturn(false);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("givetask", new Object[]{"Collect Parts", "Find three parts", 42}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        Object[] questEntry = (Object[]) computer.CurrentQuests.get(42);
        HashMap tasks = (HashMap) questEntry[0];
        Object[] taskEntry = (Object[]) tasks.get("Collect Parts");
        assertEquals("", questEntry[1]);
        assertEquals(Boolean.FALSE, taskEntry[0]);
        assertEquals("Find three parts", taskEntry[1]);
    }

    @Test
    public void dispatch_finishQuestMovesQuestToCompleted() {
        Computer computer = mock(Computer.class);
        computer.CurrentQuests = new HashMap();
        computer.CompletedQuests = new ArrayList();
        computer.connectionID = -1;
        computer.CurrentQuests.put(99, new Object[]{new HashMap(), "The Big Job"});

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("finishquest", new Object[]{99}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertFalse(computer.CurrentQuests.containsKey(99));
        assertEquals(1, computer.CompletedQuests.size());
        Object[] completed = (Object[]) computer.CompletedQuests.get(0);
        assertEquals(99, completed[0]);
        assertEquals("The Big Job", completed[1]);
    }

    @Test
    public void dispatch_requestScanBuildsAndRoutesScanPayload() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        Port port = mock(Port.class);
        PacketPort packetPort = mock(PacketPort.class);

        computer.Stats = new HashMap();
        computer.Stats.put("FireWall", 480.0f);
        computer.Ports = new HashMap();
        computer.Ports.put(4000, port);
        computer.ip = "10.0.0.1";
        computer.defaultBank = 1001;
        computer.defaultAttack = 1002;
        computer.defaultFTP = 1003;
        computer.defaultHTTP = 1004;
        computer.defaultShipping = 1005;
        computer.type = 0;

        when(computer.getLevel(anyFloat())).thenReturn(7);
        when(computer.getComputerHandler()).thenReturn(computerHandler);
        when(port.getOn()).thenReturn(true);
        when(port.getPacketPort()).thenReturn(packetPort);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("requestscan", "10.0.0.2", 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.2"));
        ApplicationData forwarded = captor.getValue();
        assertEquals("scan", forwarded.getFunction());
        assertEquals("10.0.0.1", forwarded.getSourceIP());

        Object[] payload = (Object[]) forwarded.getParameters();
        assertEquals(7, payload[0]);
        assertEquals(1001, payload[2]);
        assertEquals(1002, payload[3]);
        assertEquals(1003, payload[4]);
        assertEquals(1004, payload[5]);
        assertEquals(Boolean.FALSE, payload[6]);
        assertEquals(1005, payload[7]);
        PacketPort[] payloadPorts = (PacketPort[]) payload[1];
        assertEquals(1, payloadPorts.length);
        assertSame(packetPort, payloadPorts[0]);
    }
}
