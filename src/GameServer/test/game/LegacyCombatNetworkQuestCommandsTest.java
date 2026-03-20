package game;

import assignments.PacketAssignment;
import assignments.PacketNetwork;
import assignments.PacketPort;
import com.hackwars.game.functions.FunctionTestSupport;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import game.payload.CombatAttackXpAwardPayload;
import game.payload.CombatAttackXpUpdatePayload;
import game.payload.CombatChangeNetworkPayload;
import game.payload.CombatCheckBountyPayload;
import game.payload.CombatMakeBountyPayload;
import game.payload.CombatCompleteTaskPayload;
import game.payload.CombatDamageValues;
import game.payload.CombatExchangeCommodityPayload;
import game.payload.CombatExchangeFilePayload;
import game.payload.CombatFirewallXpPayload;
import game.payload.CombatFinishQuestPayload;
import game.payload.CombatGiveExperiencePayload;
import game.payload.CombatGiveTaskPayload;
import game.payload.CombatMiningDamageUpdatePayload;
import game.payload.CombatOpponentUpdatePayload;
import game.payload.CombatQuestInformationPayload;
import game.payload.CombatQuestTaskState;
import game.payload.CombatRequestScanPayload;
import game.payload.CombatScanPayload;
import game.payload.CombatScanSuccessPayload;
import game.payload.CombatScanXpPayload;
import game.payload.CombatSetTaskPayload;
import game.payload.CombatTakeFile2Payload;
import game.payload.CombatQuestSupport;
import game.payload.PettyCashDeltaPayload;
import game.payload.RequestWebPagePayload;
import game.payload.SaveFileRequestPayload;

public class LegacyCombatNetworkQuestCommandsTest {
    private final LegacyCombatNetworkQuestCommands handler = new LegacyCombatNetworkQuestCommands();

    @Test
    public void dispatch_returnsFalseForUnhandledCommand() {
        Computer computer = mock(Computer.class);

        boolean handled = handler.dispatch(
                computer,
                FunctionTestSupport.INSTANCE.noArgsCommand("not-owned", 0, "10.0.0.1"),
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
                new ApplicationData(new CombatGiveExperiencePayload("attack", 25.0f), 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(325.0f, (Float) computer.Stats.get("Attack"), 0.001f);
        assertTrue(computer.healthChange);
    }

    @Test
    public void dispatch_attackXpAwardUpdatesAttackStat() {
        Computer computer = mock(Computer.class);
        computer.Stats = new HashMap();
        computer.Stats.put("Attack", 300.0f);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData(new CombatAttackXpAwardPayload(25.0f), 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(325.0f, (Float) computer.Stats.get("Attack"), 0.001f);
        assertTrue(computer.healthChange);
    }

    @Test
    public void dispatch_opponentUpdateAppliesPortSnapshotAndDamage() {
        Computer computer = mock(Computer.class);
        Port port = mock(Port.class);
        computer.Stats = new HashMap();
        computer.Stats.put("Attack", 300.0f);
        computer.Ports = new HashMap();
        computer.Ports.put(4000, port);
        computer.Damage = new ArrayList();
        computer.connectionID = 1;

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData(
                        new CombatOpponentUpdatePayload(
                                new CombatDamageValues(12.0f, 13.0f, 14.0f, 15.0f, 16.0f),
                                true,
                                false,
                                true
                        ),
                        0,
                        "10.0.0.1"
                ),
                4000
        );

        assertTrue(handled);
        assertTrue(computer.healthChange);
        assertEquals(1, computer.Damage.size());
        verify(port).setTargetHP(13.0f);
        verify(port).setTargetPettyCash(14.0f);
        verify(port).setTargetCPUCost(15.0f);
        verify(port).setTargetWatch(true);
    }

    @Test
    public void dispatch_attackXpUpdateRoutesDamageToTargetIp() {
        Computer computer = mock(Computer.class);
        Port port = mock(Port.class);
        computer.Stats = new HashMap();
        computer.Stats.put("Attack", 300.0f);
        computer.Ports = new HashMap();
        computer.Ports.put(4000, port);
        computer.Damage = new ArrayList();
        computer.connectionID = 1;

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData(
                        new CombatAttackXpUpdatePayload(
                                new CombatDamageValues(12.0f, 13.0f, 14.0f, 15.0f, 16.0f),
                                false,
                                true,
                                false,
                                "10.0.0.77"
                        ),
                        0,
                        "10.0.0.1"
                ),
                4000
        );

        assertTrue(handled);
        assertTrue(computer.healthChange);
        assertEquals(1, computer.Damage.size());
        assertEquals(312.0f, (Float) computer.Stats.get("Attack"), 0.001f);
        verify(port, never()).setTargetHP(anyFloat());
        verify(port, never()).setTargetPettyCash(anyFloat());
        verify(port, never()).setTargetCPUCost(anyFloat());
        verify(port, never()).setTargetWatch(anyBoolean());
    }

    @Test
    public void dispatch_makeBountyUsesTypedPayloadsAndChargesReward() {
        Computer computer = mock(Computer.class);
        NetworkSwitch computerHandler = mock(NetworkSwitch.class);
        computer.ip = "10.0.0.1";
        computer.store = "store-ip";
        computer.MyMakeBounty = mock(MakeBounty.class);
        computer.systemChange = false;

        when(computer.checkBank()).thenReturn(true);
        when(computer.getPettyCash()).thenReturn(100.0f);
        when(computer.getCurrentTime()).thenReturn(1234L);
        when(computer.getComputerHandler()).thenReturn(computerHandler);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData(
                        new CombatMakeBountyPayload(false, "*", MakeBounty.SCAN, "", "", 1, 42.0f),
                        0,
                        "10.0.0.1"
                ),
                0
        );

        assertTrue(handled);
        assertTrue(computer.systemChange);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
        verify(computerHandler, times(2)).addData(captor.capture(), targetCaptor.capture());

        ApplicationData saveCall = captor.getAllValues().get(0);
        assertEquals("savefile", saveCall.getCommand().wireName());
        assertTrue(saveCall.getPayload() instanceof SaveFileRequestPayload);
        SaveFileRequestPayload savePayload = (SaveFileRequestPayload) saveCall.getPayload();
        assertEquals("Store/", savePayload.getPath());
        assertEquals(HackerFile.BOUNTY, savePayload.getFile().getType());
        assertEquals("Scan By (10.0.0.1)", savePayload.getFile().getName());
        assertEquals(-1, savePayload.getFile().getQuantity());
        assertEquals("store-ip", targetCaptor.getAllValues().get(0));

        ApplicationData cashCall = captor.getAllValues().get(1);
        assertEquals("pettycash", cashCall.getCommand().wireName());
        assertTrue(cashCall.getPayload() instanceof PettyCashDeltaPayload);
        assertEquals(-42.0f, ((PettyCashDeltaPayload) cashCall.getPayload()).getAmount(), 0.001f);
        assertEquals("10.0.0.1", targetCaptor.getAllValues().get(1));
    }

    @Test
    public void dispatch_giveTaskCreatesQuestEntryWhenQuestIsActive() {
        Computer computer = mock(Computer.class);
        computer.CurrentQuests = new HashMap();
        computer.CompletedQuests = new ArrayList();
        when(computer.checkQuest(42)).thenReturn(false);

        boolean handled = handler.dispatch(
                computer,
                new ApplicationData(new CombatGiveTaskPayload("Collect Parts", "Find three parts", 42), 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        Object[] questEntry = (Object[]) computer.CurrentQuests.get(42);
        HashMap tasks = (HashMap) questEntry[0];
        CombatQuestTaskState taskEntry = (CombatQuestTaskState) tasks.get("Collect Parts");
        assertEquals("", questEntry[1]);
        assertEquals(Boolean.FALSE, taskEntry.getCompleted());
        assertEquals("Find three parts", taskEntry.getLabel());
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
                new ApplicationData(new CombatFinishQuestPayload(99), 0, "10.0.0.1"),
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
                new ApplicationData(new CombatRequestScanPayload("10.0.0.2"), 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.2"));
        ApplicationData forwarded = captor.getValue();
        assertEquals("scan", forwarded.getCommand().wireName());
        assertEquals("10.0.0.1", forwarded.getSourceIP());
        CombatScanPayload payload = (CombatScanPayload) forwarded.getPayload();
        assertEquals(7, payload.getOpponentFirewall());
        assertEquals(1001, payload.getDefaultBank());
        assertEquals(1002, payload.getDefaultAttack());
        assertEquals(1003, payload.getDefaultFtp());
        assertEquals(1004, payload.getDefaultHttp());
        assertFalse(payload.getNpc());
        assertEquals(1005, payload.getDefaultShipping());
        PacketPort[] payloadPorts = payload.getPacketPorts();
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
                new ApplicationData(
                        new CombatScanPayload(3, new PacketPort[]{packetPort}, 1001, 8080, 1003, 1004, false, 1005),
                        0,
                        "10.0.0.9"
                ),
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

        assertEquals("pettycash", payloads.get(0).getCommand().wireName());
        assertTrue(payloads.get(0).getPayload() instanceof PettyCashDeltaPayload);
        assertEquals(-10.0f, ((PettyCashDeltaPayload) payloads.get(0).getPayload()).getAmount(), 0.001f);
        assertEquals("10.0.0.1", targets.get(0));

        assertEquals("scanxp", payloads.get(1).getCommand().wireName());
        assertTrue(payloads.get(1).getPayload() instanceof CombatScanXpPayload);
        assertEquals(60.0f, ((CombatScanXpPayload) payloads.get(1).getPayload()).getAmount(), 0.001f);
        assertEquals("10.0.0.1", targets.get(1));

        assertEquals("scansuccess", payloads.get(2).getCommand().wireName());
        assertTrue(payloads.get(2).getPayload() instanceof CombatScanSuccessPayload);
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
                new ApplicationData(
                        new CombatScanPayload(30, new PacketPort[]{packetPort}, 1001, 8080, 1003, 4040, true, 1005),
                        0,
                        "10.0.0.9"
                ),
                0
        );

        assertTrue(handled);
        assertEquals(-1, packetPort.getDefault());
        assertNull(packetPort.getFireWall());
        assertEquals("", packetPort.getNote());

        ArgumentCaptor<ApplicationData> payloadCaptor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler, times(3)).addData(payloadCaptor.capture(), anyString());
        assertTrue(payloadCaptor.getAllValues().get(1).getPayload() instanceof CombatScanXpPayload);
        assertEquals(20.0f, ((CombatScanXpPayload) payloadCaptor.getAllValues().get(1).getPayload()).getAmount(), 0.001f);
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
        computer.network = "ProgNet";
        computer.pettyCash = 99.0f;

        HashMap tasks = new HashMap();
        tasks.put("SpeakToNPC", new CombatQuestTaskState(true, "Talk to the quest giver"));
        computer.CurrentQuests.put(7, new Object[]{tasks, "Active Quest"});
        computer.CompletedQuests.add(new Object[]{9, "Done Quest"});

        when(computer.getComputerHandler()).thenReturn(computerHandler);
        when(computer.getMyFileSystem()).thenReturn(fileSystem);
        when(computer.getCurrentQuests()).thenReturn(computer.CurrentQuests);
        when(computer.getWatchHandler()).thenReturn(watchHandler);
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
                new ApplicationData(new CombatQuestInformationPayload(parameters, interestedQuests), 0, "10.0.0.8"),
                0
        );

        assertTrue(handled);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.8"));
        ApplicationData forwarded = captor.getValue();
        assertEquals("requestwebpage", forwarded.getCommand().wireName());
        assertTrue(forwarded.getPayload() instanceof RequestWebPagePayload);

        HashMap routed = ((RequestWebPagePayload) forwarded.getPayload()).getRequestParameters();
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
                new ApplicationData(new CombatTakeFile2Payload("artifact-id", 2), 0, "10.0.0.1"),
                0
        );

        assertTrue(handled);
        assertEquals(3, fileSystem.getFile("", "ArtifactFile").getQuantity());
        assertTrue(computer.PA.requestPrimary());
        assertEquals(1, computer.PA.getRequestPrimaryID());
        assertTrue(computer.systemChange);

        ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
        verify(computerHandler).addData(captor.capture(), eq("10.0.0.1"));
        assertEquals("message", captor.getValue().getCommand().wireName());
        assertTrue(captor.getValue().getPayload() instanceof game.payload.StructuredMessagePayload);
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
                    new ApplicationData(new CombatChangeNetworkPayload("ProgNet"), 0, "10.0.0.1"),
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
        Field field = Network.class.getDeclaredField("networkSingleton");
        field.setAccessible(true);
        Network previous = (Network) field.get(null);
        field.set(null, replacement);
        return previous;
    }

    private void restoreNetworkSingleton(Network previous) throws Exception {
        Field field = Network.class.getDeclaredField("networkSingleton");
        field.setAccessible(true);
        field.set(null, previous);
    }
}
