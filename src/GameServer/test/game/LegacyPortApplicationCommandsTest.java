package game;

import assignments.PacketAssignment;
import assignments.PacketPort;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegacyPortApplicationCommandsTest {
    private final LegacyPortApplicationCommands handler = new LegacyPortApplicationCommands();

    @Test
    public void dispatch_requestSecondaryDirectory_returnsFalse() {
        Computer computer = baseComputer("1.1.1.1");

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("requestsecondarydirectory", new Object[]{"9.9.9.9", "Public/", 7}, 0, "source"),
            13
        );

        assertFalse(handled);
    }

    @Test
    public void dispatch_fetchPorts_populatesPacketAssignment() {
        Computer computer = baseComputer("2.2.2.2");
        PacketPort packetPort = new PacketPort();
        packetPort.setNumber(45);
        Port port = mock(Port.class);
        when(port.getPacketPort()).thenReturn(packetPort);
        computer.Ports.put(45, port);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("fetchports", null, 0, "source"),
            0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        assertEquals(1, computer.PA.getPacketPorts().length);
        assertSame(packetPort, computer.PA.getPacketPorts()[0]);
    }

    @Test
    public void dispatch_deleteFirewall_movesFirewallToDiskAndRefreshesPorts() {
        Computer computer = baseComputer("3.3.3.3");
        when(computer.MyFileSystem.getSpaceLeft()).thenReturn(1);
        when(computer.checkRename(any(HackerFile.class), eq(""))).thenAnswer(invocation -> invocation.getArgument(0));

        HackerFile installed = new HackerFile(HackerFile.NEW_FIREWALL);
        installed.setName("Shield");
        installed.setQuantity(0);

        NewFireWall firewall = mock(NewFireWall.class);
        when(firewall.getHackerFile()).thenReturn(installed);

        Port port = mock(Port.class);
        when(port.getNumber()).thenReturn(22);
        when(port.getFireWall()).thenReturn(firewall);
        computer.Ports.put(22, port);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("deletefirewall", Integer.valueOf(22), 0, "source"),
            0
        );

        assertTrue(handled);
        verify(computer.MyFileSystem).addFile(installed, false);
        verify(port).setFireWall(any(HackerFile.class));
        ArgumentCaptor<Object[]> parameterCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(computer).addMessage(eq(MessageHandler.REMOVE_FIREWALL_SUCCESS), parameterCaptor.capture());
        assertEquals("Shield", parameterCaptor.getValue()[0]);
        assertFetchPortsRefresh(computer.MyComputerHandler, "3.3.3.3");
    }

    @Test
    public void dispatch_installFirewall_whenLevelTooLow_addsFailureAndRefreshesPorts() {
        Computer computer = baseComputer("4.4.4.4");
        HackerFile firewallFile = new HackerFile(HackerFile.NEW_FIREWALL);
        HashMap content = new HashMap();
        content.put("equip_level", "5");
        firewallFile.setContent(content);
        when(computer.MyFileSystem.getFile("Public/", "wall")).thenReturn(firewallFile);
        computer.Stats.put("FireWall", 0.0f);
        when(computer.getLevel(anyFloat())).thenReturn(0);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("installfirewall", new Object[]{"Public/", "wall"}, 0, "source"),
            17
        );

        assertTrue(handled);
        ArgumentCaptor<Object[]> parameterCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(computer).addMessage(eq(MessageHandler.INSTALL_FIREWALL_FAIL_LEVEL), parameterCaptor.capture());
        assertEquals(5, parameterCaptor.getValue()[0]);
        assertFetchPortsRefresh(computer.MyComputerHandler, "4.4.4.4");
    }

    @Test
    public void dispatch_installApplication_whenMemoryIsFull_addsFailureWithoutRefresh() {
        Computer computer = baseComputer("5.5.5.5");
        computer.memorytype = 0;

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("installapplication", new Object[]{"Public/", "bank"}, 0, "source"),
            (int) Computer.MEMORY_CHART[0]
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);
        verify(computer).addMessage(MessageHandler.MAX_PROGRAMS_REACHED);
        verify(computer.MyComputerHandler, never()).addData(any(ApplicationData.class), any(String.class));
    }

    @Test
    public void dispatch_replaceApplication_whenFileMissing_stillRefreshesPorts() {
        Computer computer = baseComputer("6.6.6.6");
        when(computer.MyFileSystem.getFile("Public/", "ftp")).thenReturn(null);

        boolean handled = handler.dispatch(
            computer,
            new ApplicationData("replaceapplication", new Object[]{"Public/", "ftp"}, 0, "source"),
            18
        );

        assertTrue(handled);
        assertFetchPortsRefresh(computer.MyComputerHandler, "6.6.6.6");
    }

    private Computer baseComputer(String ip) {
        Computer computer = mock(Computer.class);
        computer.ip = ip;
        computer.PA = new PacketAssignment(0);
        computer.Ports = new HashMap();
        computer.Stats = new HashMap();
        computer.MyComputerHandler = mock(NetworkSwitch.class);
        computer.MyFileSystem = mock(FileSystem.class);
        computer.MyEquipmentSheet = mock(EquipmentSheet.class);
        computer.Choices = new java.util.ArrayList();
        computer.cputype = 0;
        computer.memorytype = 0;

        when(computer.getCPULoad()).thenReturn(0f);
        when(computer.MyEquipmentSheet.getCPUBonus()).thenReturn(0f);
        when(computer.checkRename(any(HackerFile.class), eq(""))).thenAnswer(invocation -> invocation.getArgument(0));

        return computer;
    }

    private void assertFetchPortsRefresh(NetworkSwitch networkSwitch, String ip) {
        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(networkSwitch).addData(captor.capture(), eq(ip));
        assertEquals("fetchports", captor.getValue().getFunction());
    }
}
