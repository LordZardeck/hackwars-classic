package game;

import assignments.PacketAssignment;
import assignments.PacketNetwork;
import assignments.PacketPort;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import util.Time;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @Test
    public void dispatch_scanSuccessMarksDefaultAndSendsCharges() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        MakeBounty makeBounty = mock(MakeBounty.class);
        PacketPort packetPort = new PacketPort();
        HashMap firewall = new HashMap();

        packetPort.setType(PacketPort.ATTACK);
        packetPort.setNumber(8080);
        packetPort.setFireWall(firewall);

        computer.Stats = new HashMap();
        computer.Stats.put("Scanning", 900.0f);
        computer.PA = new PacketAssignment(0);
        computer.ip = "10.0.0.1";
        computer.MyMakeBounty = makeBounty;

        when(computer.getLevel(anyFloat())).thenReturn(30);
        when(computer.getCPULoad()).thenReturn(5.0f);
        when(computer.getMaximumCPULoad()).thenReturn(50.0f);
        when(computer.checkBank()).thenReturn(true);
        when(computer.getPettyCash()).thenReturn(100.0f);
        when(computer.isNPC()).thenReturn(false);
        when(computer.getComputerHandler()).thenReturn(computerHandler);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("scan", new Object[]{3, new PacketPort[]{packetPort}, 1001, 8080, 1003, 1004, Boolean.FALSE, 1005}, 0, "10.0.0.9"),
                0
        );

        assertTrue(handled);
        assertEquals(1, packetPort.getDefault());
        assertEquals("10.0.0.9", packetPort.getNote());
        assertSame(firewall, packetPort.getFireWall());
        assertSame(packetPort, computer.PA.getScannedPorts()[0]);

        ArgumentCaptor<ApplicationData> payloadCaptor = ArgumentCaptor.forClass(ApplicationData.class);
        ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
        verify(computerHandler, times(3)).addData(payloadCaptor.capture(), targetCaptor.capture());
        List<ApplicationData> payloads = payloadCaptor.getAllValues();
        List<String> targets = targetCaptor.getAllValues();

        assertEquals("pettycash", payloads.get(0).getFunction());
        assertEquals(-10.0f, (Float) payloads.get(0).getParameters(), 0.001f);
        assertEquals("10.0.0.1", targets.get(0));

        assertEquals("scanxp", payloads.get(1).getFunction());
        assertEquals(60.0f, (Float) payloads.get(1).getParameters(), 0.001f);
        assertEquals("10.0.0.1", targets.get(1));

        assertEquals("scansuccess", payloads.get(2).getFunction());
        assertEquals("10.0.0.9", targets.get(2));
    }

    @Test
    public void dispatch_scanLowLevelRedactsFirewallAndUsesReducedXp() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        PacketPort packetPort = new PacketPort();

        packetPort.setType(PacketPort.HTTP);
        packetPort.setNumber(4040);
        packetPort.setFireWall(new HashMap());

        computer.Stats = new HashMap();
        computer.Stats.put("Scanning", 300.0f);
        computer.PA = new PacketAssignment(0);
        computer.ip = "10.0.0.1";
        computer.MyMakeBounty = mock(MakeBounty.class);

        when(computer.getLevel(anyFloat())).thenReturn(10);
        when(computer.getCPULoad()).thenReturn(5.0f);
        when(computer.getMaximumCPULoad()).thenReturn(50.0f);
        when(computer.checkBank()).thenReturn(true);
        when(computer.getPettyCash()).thenReturn(100.0f);
        when(computer.isNPC()).thenReturn(true);
        when(computer.getComputerHandler()).thenReturn(computerHandler);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("scan", new Object[]{30, new PacketPort[]{packetPort}, 1001, 8080, 1003, 4040, Boolean.TRUE, 1005}, 0, "10.0.0.9"),
                0
        );

        assertTrue(handled);
        assertEquals(-1, packetPort.getDefault());
        assertNull(packetPort.getFireWall());
        assertEquals("", packetPort.getNote());

        ArgumentCaptor<ApplicationData> payloadCaptor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler, times(3)).addData(payloadCaptor.capture(), anyString());
        assertEquals(20.0f, (Float) payloadCaptor.getAllValues().get(1).getParameters(), 0.001f);
    }

    @Test
    public void dispatch_questInformationInjectsQuestItemFlagsAndProfileData() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        WatchHandler watchHandler = mock(WatchHandler.class);
        FileSystem fileSystem = createFileSystem("10.0.0.50");

        HackerFile questItem = new HackerFile(HackerFile.QUEST_ITEM);
        HashMap itemContent = new HashMap();
        itemContent.put("itemname", "artifact");
        questItem.setContent(itemContent);
        questItem.setLocation("");
        questItem.setName("ArtifactFile");
        questItem.setQuantity(3);
        fileSystem.addFile(questItem, true);

        computer.MyFileSystem = fileSystem;
        computer.MyWatchHandler = watchHandler;
        computer.CurrentQuests = new HashMap();
        computer.CompletedQuests = new ArrayList();
        computer.commodityAmount = new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        computer.pageBody = "<html>ready</html>";
        computer.ip = "10.0.0.1";

        HashMap tasks = new HashMap();
        tasks.put("SpeakToNPC", new Object[]{Boolean.TRUE, "Talk to the quest giver"});
        computer.CurrentQuests.put(7, new Object[]{tasks, "Active Quest"});
        computer.CompletedQuests.add(new Object[]{9, "Done Quest"});

        when(computer.getComputerHandler()).thenReturn(computerHandler);
        when(computer.getAttackLevel()).thenReturn(11.0f);
        when(computer.getBankLevel()).thenReturn(12.0f);
        when(computer.getWatchLevel()).thenReturn(13.0f);
        when(computer.getScanningLevel()).thenReturn(14.0f);
        when(computer.getFireWallLevel()).thenReturn(15.0f);
        when(computer.getHTTPLevel()).thenReturn(16.0f);
        when(computer.getRedirectingLevel()).thenReturn(17.0f);
        when(computer.getRepairLevel()).thenReturn(18.0f);
        when(computer.getPettyCash()).thenReturn(99.0f);
        when(computer.getDefaultAttack()).thenReturn(2001);
        when(computer.getDefaultBank()).thenReturn(2002);
        when(computer.getDefaultHTTP()).thenReturn(2003);
        when(computer.getDefaultShipping()).thenReturn(2004);
        when(computer.getRepaired()).thenReturn(true);
        when(computer.getNetwork()).thenReturn("ProgNet");
        when(computer.checkFirewall()).thenReturn(true);
        when(watchHandler.getWatchCount()).thenReturn(1);

        HashMap parameters = new HashMap();
        ArrayList interestedQuests = new ArrayList();
        interestedQuests.add(7);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("questinformation", new Object[]{parameters, interestedQuests}, 0, "10.0.0.8"),
                0
        );

        assertTrue(handled);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.8"));
        ApplicationData forwarded = captor.getValue();
        assertEquals("requestwebpage", forwarded.getFunction());

        HashMap routed = (HashMap) forwarded.getParameters();
        assertEquals("true", routed.get("artifact"));
        assertEquals("3", routed.get("artifact_quantity"));
        assertEquals("true", routed.get("SpeakToNPC"));
        assertEquals("true", routed.get("quest9"));
        assertEquals("false", routed.get("quest7"));
        assertEquals("5.0", routed.get("commodity4"));
        assertEquals("11.0", routed.get("Attack"));
        assertEquals("99.0", routed.get("pettycash"));
        assertEquals("2002", routed.get("defaultbank"));
        assertEquals("2001", routed.get("defaultattack"));
        assertEquals("2003", routed.get("defaulthttp"));
        assertEquals("2004", routed.get("defaultredirecting"));
        assertEquals("true", routed.get("ProgNet"));
        assertEquals("true", routed.get("websitemade"));
        assertEquals("true", routed.get("firewallinstalled"));
        assertEquals("true", routed.get("watchinstalled"));
    }

    @Test
    public void dispatch_takeFile2ReducesQuestItemQuantityAndRequestsRefresh() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        FileSystem fileSystem = createFileSystem("10.0.0.60");

        HackerFile questItem = new HackerFile(HackerFile.QUEST_ITEM);
        HashMap itemContent = new HashMap();
        itemContent.put("itemname", "artifact-id");
        questItem.setContent(itemContent);
        questItem.setLocation("");
        questItem.setName("ArtifactFile");
        questItem.setQuantity(5);
        fileSystem.addFile(questItem, true);

        computer.MyFileSystem = fileSystem;
        computer.PA = new PacketAssignment(0);
        computer.ip = "10.0.0.1";
        computer.connectionID = -1;

        when(computer.getComputerHandler()).thenReturn(computerHandler);
        when(computer.checkRename(any(HackerFile.class), eq(""))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData("takefile2", new Object[]{"artifact-id", 2}, 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(3, fileSystem.getFile("", "ArtifactFile").getQuantity());
        assertTrue(computer.PA.requestPrimary());
        assertEquals(1, computer.PA.getRequestPrimaryID());
        assertTrue(computer.systemChange);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.1"));
        assertEquals("message", captor.getValue().getFunction());
    }

    @Test
    public void dispatch_changeNetworkMovesToAllowedNetworkAndUpdatesPacket() throws Exception {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        Network network = mock(Network.class);
        PacketNetwork packetNetwork = new PacketNetwork();
        packetNetwork.setStoreIP("10.0.0.9");

        computer.AllowedNetworks = new ArrayList();
        computer.AllowedNetworks.add("ProgNet");
        computer.network = Network.ROOT_NETWORK;
        computer.ip = "10.0.0.1";
        computer.PA = new PacketAssignment(0);
        computer.MyTime = mock(Time.class);
        computer.lastChangeNetwork = 0L;

        when(computer.getComputerHandler()).thenReturn(computerHandler);
        when(computer.MyTime.getCurrentTime()).thenReturn(Computer.CHANGE_NETWORKS + 1000L);
        when(network.getNetworkInformation("ProgNet")).thenReturn(packetNetwork);

        Network previous = swapNetworkSingleton(network);
        try {
            boolean handled = handler.dispatch(
                    computer,
                    new ApplicationData("changenetwork", "ProgNet", 0, "10.0.0.1"),
                    0
            );

            assertTrue(handled);
            assertEquals("ProgNet", computer.network);
            assertEquals("10.0.0.9", computer.store);
            assertSame(packetNetwork, computer.PA.getPacketNetwork());
            assertTrue(computer.systemChange);
            verify(network).removeFromNetwork(Network.ROOT_NETWORK, "10.0.0.1");
            verify(network).addToNetwork("ProgNet", "10.0.0.1");
        } finally {
            restoreNetworkSingleton(previous);
        }
    }

    private FileSystem createFileSystem(String ip) {
        Computer storageComputer = mock(Computer.class);
        EquipmentSheet equipmentSheet = mock(EquipmentSheet.class);
        when(storageComputer.getEquipmentSheet()).thenReturn(equipmentSheet);
        when(storageComputer.getCurrentTime()).thenReturn(0L);
        when(storageComputer.getIP()).thenReturn(ip);
        when(equipmentSheet.getHDBonus()).thenReturn(0);

        FileSystem fileSystem = new FileSystem(storageComputer);
        fileSystem.setHDType(5);
        return fileSystem;
    }

    private Network swapNetworkSingleton(Network replacement) throws Exception {
        Field field = Network.class.getDeclaredField("myNetworkSingleton");
        field.setAccessible(true);
        Network previous = (Network) field.get(null);
        field.set(null, replacement);
        return previous;
    }

    private void restoreNetworkSingleton(Network previous) throws Exception {
        Field field = Network.class.getDeclaredField("myNetworkSingleton");
        field.setAccessible(true);
        field.set(null, previous);
    }
}
