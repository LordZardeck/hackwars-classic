package game;

import assignments.PacketAssignment;
import org.junit.Assert;
import org.junit.Test;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;

public class LegacyEconomyWebSocialCommandsTest {
    @Test
    public void dispatchReturnsFalseForUnhandledCommand() {
        TestFixture fixture = new TestFixture();
        try {
            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("unhandled", null, 0, fixture.computer.ip),
                0
            );

            Assert.assertFalse(handled);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void setPreferencesStoresProvidedPreferences() {
        TestFixture fixture = new TestFixture();
        try {
            HashMap preferences = new HashMap();
            preferences.put("music", Boolean.TRUE);

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("setpreferences", new Object[]{"ignored", preferences}, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertSame(preferences, fixture.computer.preferences);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void requestPageCopiesSavedContentIntoThePacket() {
        TestFixture fixture = new TestFixture();
        try {
            fixture.computer.PA = new PacketAssignment(0);
            fixture.computer.pageTitle = "Welcome";
            fixture.computer.pageBody = "Hello";

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("requestpage", null, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertTrue(fixture.computer.systemChange);
            Assert.assertEquals("Welcome", fixture.computer.PA.getTitle());
            Assert.assertEquals("Hello", fixture.computer.PA.getBody());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void savePageRejectsOversizedBody() {
        TestFixture fixture = new TestFixture();
        try {
            StringBuilder builder = new StringBuilder(30001);
            for (int i = 0; i < 30001; i++) {
                builder.append('a');
            }

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("savepage", new Object[]{"Title", builder.toString()}, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertTrue(fixture.computer.systemChange);
            Assert.assertEquals("", fixture.computer.pageTitle);
            Assert.assertEquals("", fixture.computer.pageBody);
            Assert.assertFalse(fixture.computer.pageChanged);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void pettyCashTransferWithActiveBankCreditsCashAndNotifiesSender() {
        TestFixture fixture = new TestFixture();
        try {
            fixture.addPort(11, Port.BANKING, true);
            fixture.seedSkillStats(100000.0f);
            fixture.computer.pettyCash = 25.0f;

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("pettycash", Float.valueOf(50.0f), 0, "2.2.2.2"),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(75.0f, fixture.computer.pettyCash, 0.0001f);
            Assert.assertEquals(1, fixture.dispatches.size());
            CapturedDispatch dispatch = fixture.dispatches.get(0);
            Assert.assertEquals("2.2.2.2", dispatch.targetIp);
            Assert.assertEquals("message", dispatch.applicationData.getFunction());
            Assert.assertEquals(1, fixture.computer.getMessages().size());
            Assert.assertTrue(fixture.latestMessageText().contains("Received transfer of $50.00 from 2.2.2.2."));
        } finally {
            fixture.close();
        }
    }

    @Test
    public void pettyCashTransferWithoutBankReturnsFundsAndSendsFailure() {
        TestFixture fixture = new TestFixture();
        try {
            fixture.seedSkillStats(100000.0f);
            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("pettycash", new Object[]{Float.valueOf(50.0f), Float.valueOf(50.0f)}, 0, "2.2.2.2"),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(0.0f, fixture.computer.pettyCash, 0.0001f);
            Assert.assertEquals(2, fixture.dispatches.size());
            Assert.assertEquals("pettycash", fixture.dispatches.get(0).applicationData.getFunction());
            Assert.assertEquals(Float.valueOf(50.0f), fixture.dispatches.get(0).applicationData.getParameters());
            Assert.assertEquals("message", fixture.dispatches.get(1).applicationData.getFunction());
            Assert.assertSame(MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT, fixture.dispatches.get(1).applicationData.getParameters());
            Assert.assertTrue(fixture.latestMessageText().contains("Could not recieve transfer of $50.00 from 2.2.2.2."));
        } finally {
            fixture.close();
        }
    }

    @Test
    public void requestPurchaseReservesStoreInventoryAndQueuesContinuePurchase() {
        TestFixture fixture = new TestFixture();
        try {
            fixture.computer.storeRevenueTarget = "store-revenue";
            HackerFile offer = fixture.addStoreFile("bundle.bin", 3, 15.0f);

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("requestpurchase", new Object[]{"bundle.bin", Integer.valueOf(2)}, 0, "buyer-ip"),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(1, offer.getQuantity());
            Assert.assertEquals(1, fixture.dispatches.size());
            CapturedDispatch dispatch = fixture.dispatches.get(0);
            Assert.assertEquals("buyer-ip", dispatch.targetIp);
            Assert.assertEquals("continuepurchase", dispatch.applicationData.getFunction());
            Object[] payload = (Object[]) dispatch.applicationData.getParameters();
            HackerFile reserved = (HackerFile) payload[0];
            Assert.assertEquals("bundle.bin", reserved.getName());
            Assert.assertEquals(2, reserved.getQuantity());
            Assert.assertEquals("store-revenue", payload[1]);
            Assert.assertEquals(Integer.valueOf(fixture.computer.type), payload[2]);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void continuePurchaseQueuesSaveTransfersAndRefreshesOnSuccessfulSoftwarePurchase() {
        TestFixture fixture = new TestFixture();
        try {
            fixture.addPort(11, Port.BANKING, true);
            fixture.seedSkillStats(0.0f);
            fixture.computer.pettyCash = 500.0f;

            HackerFile file = new HackerFile(HackerFile.TEXT);
            file.setName("guide.txt");
            file.setContent(new HashMap());
            file.setPrice(10.0f);
            file.setQuantity(1);

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("continuepurchase", new Object[]{file, "seller-bank", Integer.valueOf(0)}, 0, "seller-site"),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(5, fixture.dispatches.size());
            Assert.assertEquals("savefile", fixture.dispatches.get(0).applicationData.getFunction());
            Assert.assertEquals(fixture.computer.ip, fixture.dispatches.get(0).targetIp);
            Assert.assertEquals("pettycash", fixture.dispatches.get(1).applicationData.getFunction());
            Assert.assertEquals(fixture.computer.ip, fixture.dispatches.get(1).targetIp);
            Assert.assertEquals("pettycash", fixture.dispatches.get(2).applicationData.getFunction());
            Assert.assertEquals("seller-bank", fixture.dispatches.get(2).targetIp);
            Assert.assertEquals("requestwebpage", fixture.dispatches.get(3).applicationData.getFunction());
            Assert.assertEquals("seller-site", fixture.dispatches.get(3).targetIp);
            Assert.assertEquals("requestequipment", fixture.dispatches.get(4).applicationData.getFunction());
            Assert.assertEquals(fixture.computer.ip, fixture.dispatches.get(4).targetIp);
            Assert.assertTrue(fixture.latestMessageText().contains("You have successfully purchased 1 guide.txt"));
        } finally {
            fixture.close();
        }
    }

    @Test
    public void requestWebpageWithoutHttpPortServesFallbackErrorPage() {
        TestFixture fixture = new TestFixture();
        try {
            HashMap parameters = new HashMap();
            parameters.put("packetid", Integer.valueOf(77));

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("requestwebpage", parameters, 0, "browser-ip"),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(1, fixture.dispatches.size());
            CapturedDispatch dispatch = fixture.dispatches.get(0);
            Assert.assertEquals("browser-ip", dispatch.targetIp);
            Assert.assertEquals("webpage", dispatch.applicationData.getFunction());
            Object[] payload = (Object[]) dispatch.applicationData.getParameters();
            Assert.assertEquals("Server Not Found", payload[0]);
            Assert.assertTrue(((String) payload[1]).contains("HTTP Status 408"));
            Assert.assertEquals(Integer.valueOf(77), payload[3]);
        } finally {
            fixture.close();
        }
    }

    private static final class TestFixture {
        private final Time time = new Time();
        private final Computer computer = new Computer("10.0.0.1", null, time, -1, null);
        private final LegacyEconomyWebSocialCommands handler = new LegacyEconomyWebSocialCommands();
        private final ArrayList<CapturedDispatch> dispatches = new ArrayList<CapturedDispatch>();

        private TestFixture() {
            stopComputerThread(computer);
            computer.connectionID = 1;
            computer.systemChange = false;
            computer.pageTitle = "";
            computer.pageBody = "";
            computer.pageChanged = false;
            seedSkillStats(0.0f);
            computer.MyFileSystem.addDirectory("Store/");
            computer.MyFileSystem.addDirectory("Public/");
            computer.MyComputerHandler = new CapturingNetworkSwitch(computer, dispatches);
        }

        private void close() {
            stopComputerThread(computer);
            time.clean();
        }

        private void addPort(int number, int type, boolean on) {
            Port port = new Port(computer, computer.getComputerHandler());
            port.setNumber(number);
            port.setType(type);
            port.setOn(on);
            port.setDummy(false);
            computer.Ports.put(Integer.valueOf(number), port);
        }

        private HackerFile addStoreFile(String name, int quantity, float price) {
            HackerFile file = new HackerFile(HackerFile.TEXT);
            file.setName(name);
            file.setQuantity(quantity);
            file.setPrice(price);
            file.setMaker("Alexander");
            file.setContent(new HashMap());
            file.setLocation("Store/");
            Assert.assertTrue(computer.MyFileSystem.addFile(file, true));
            return file;
        }

        private void seedSkillStats(float xp) {
            computer.Stats.put("Attack", Float.valueOf(xp));
            computer.Stats.put("Bank", Float.valueOf(xp));
            computer.Stats.put("Watch", Float.valueOf(xp));
            computer.Stats.put("Scanning", Float.valueOf(xp));
            computer.Stats.put("Webdesign", Float.valueOf(xp));
            computer.Stats.put("Redirecting", Float.valueOf(xp));
            computer.Stats.put("Repair", Float.valueOf(xp));
            computer.Stats.put("FireWall", Float.valueOf(xp));
        }

        private String latestMessageText() {
            Assert.assertFalse(computer.getMessages().isEmpty());
            Object[] latest = (Object[]) computer.getMessages().get(computer.getMessages().size() - 1);
            return (String) latest[0];
        }
    }

    private static final class CapturedDispatch {
        private final ApplicationData applicationData;
        private final String targetIp;

        private CapturedDispatch(ApplicationData applicationData, String targetIp) {
            this.applicationData = applicationData;
            this.targetIp = targetIp;
        }
    }

    private static final class CapturingNetworkSwitch extends NetworkSwitch {
        private final ArrayList<CapturedDispatch> dispatches;

        private CapturingNetworkSwitch(Computer computer, ArrayList<CapturedDispatch> dispatches) {
            super(computer, null);
            this.dispatches = dispatches;
        }

        @Override
        public void addData(ApplicationData applicationData, String ip) {
            dispatches.add(new CapturedDispatch(applicationData, ip));
        }
    }

    private static void stopComputerThread(Computer computer) {
        computer.run = false;
        Thread thread = computer.MyThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        computer.MyThread = null;
    }
}
