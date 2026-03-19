package game;

import assignments.PacketWatch;
import org.junit.Assert;
import org.junit.Test;
import util.Time;

public class LegacyWatchEquipmentCommandsTest {
    @Test
    public void dispatch_returnsFalse_forNonOwnedCommand() {
        TestFixture fixture = new TestFixture();
        try {
            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("not-owned", null, 0, fixture.computer.ip),
                0
            );

            Assert.assertFalse(handled);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void dispatch_fetchWatches_populatesPacketAssignment() {
        TestFixture fixture = new TestFixture();
        try {
            Watch watch = new Watch(fixture.computer);
            watch.setType(Watch.PETTY_CASH);
            watch.setOn(true);
            watch.setNote("alarm");
            watch.setCPUCost(2.5f);
            watch.setSearchFireWall(3);
            watch.setQuantity(125.0f);
            watch.setPort(9);
            fixture.computer.MyWatchHandler.addWatch(watch);
            fixture.computer.systemChange = false;

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("fetchwatches", null, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertTrue(fixture.computer.systemChange);
            PacketWatch[] packetWatches = fixture.computer.PA.getPacketWatches();
            Assert.assertEquals(1, packetWatches.length);
            Assert.assertEquals(Watch.PETTY_CASH, packetWatches[0].getType());
            Assert.assertEquals("alarm", packetWatches[0].getNote());
            Assert.assertTrue(packetWatches[0].getOn());
            Assert.assertEquals(9, packetWatches[0].getPort());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void dispatch_changeWatchType_updatesWatchAndQueuesRefresh() {
        TestFixture fixture = new TestFixture();
        try {
            Watch watch = new Watch(fixture.computer);
            watch.setType(Watch.HEALTH);
            fixture.computer.MyWatchHandler.addWatch(watch);
            fixture.computer.Tasks.clear();

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("changewatchtype", new Integer[]{0, Watch.SCAN}, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(Watch.SCAN, watch.getType());
            assertQueuedFetchWatches(fixture);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void dispatch_setWatchOnOff_turnsWatchOnAndQueuesRefreshWhenWithinLimits() {
        TestFixture fixture = new TestFixture();
        try {
            Watch watch = new Watch(fixture.computer);
            watch.setType(Watch.PETTY_CASH);
            watch.setCPUCost(3.0f);
            watch.setOn(false);
            fixture.computer.MyWatchHandler.addWatch(watch);
            fixture.computer.Tasks.clear();

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("setwatchonoff", new Object[]{0, Boolean.TRUE}, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertTrue(watch.getOn());
            assertQueuedFetchWatches(fixture);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void dispatch_setWatchObservedPorts_updatesObservedPortsAndQueuesRefresh() {
        TestFixture fixture = new TestFixture();
        try {
            Watch watch = new Watch(fixture.computer);
            fixture.computer.MyWatchHandler.addWatch(watch);
            fixture.computer.Tasks.clear();

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("setwatchobservedports", new Object[]{0, new Integer[]{3, 7, 9}}, 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertEquals(3, watch.getObservedPorts().size());
            Assert.assertEquals(3, watch.getObservedPorts().get(0));
            Assert.assertEquals(7, watch.getObservedPorts().get(1));
            Assert.assertEquals(9, watch.getObservedPorts().get(2));
            assertQueuedFetchWatches(fixture);
        } finally {
            fixture.close();
        }
    }

    @Test
    public void dispatch_requestEquipment_populatesPrimaryAndSecondaryDirectories() {
        TestFixture fixture = new TestFixture();
        try {
            HackerFile agpCard = fixture.createEquipmentFile(HackerFile.AGP, "Alpha AGP");
            fixture.computer.MyFileSystem.addFile(agpCard, false);
            fixture.computer.systemChange = false;

            boolean handled = fixture.handler.dispatch(
                fixture.computer,
                new ApplicationData("requestequipment", Integer.valueOf(13), 0, fixture.computer.ip),
                0
            );

            Assert.assertTrue(handled);
            Assert.assertTrue(fixture.computer.systemChange);
            Assert.assertNotNull(fixture.computer.PA.getDirectory());
            Assert.assertEquals(13, fixture.computer.PA.getDirectory()[0]);
            Assert.assertNotNull(fixture.computer.PA.getSecondaryDirectory());
            Assert.assertEquals(13, fixture.computer.PA.getSecondaryDirectory()[0]);
            HackerFile returned = (HackerFile) fixture.computer.PA.getDirectory()[1];
            Assert.assertEquals("Alpha AGP", returned.getName());
            Assert.assertNotNull(returned.getContent().get("bonusdata"));
        } finally {
            fixture.close();
        }
    }

    private static void assertQueuedFetchWatches(TestFixture fixture) {
        Assert.assertEquals(1, fixture.computer.Tasks.size());
        ApplicationData queued = (ApplicationData) fixture.computer.Tasks.get(0);
        Assert.assertEquals("fetchwatches", queued.getFunction());
    }

    private static final class TestFixture {
        private final Time time = new Time();
        private final Computer computer = new Computer("10.0.0.1", null, time, -1, null);
        private final LegacyWatchEquipmentCommands handler = new LegacyWatchEquipmentCommands();

        private TestFixture() {
            stopComputerThread(computer);
            computer.Tasks.clear();
        }

        private void close() {
            stopComputerThread(computer);
            time.clean();
        }

        private HackerFile createEquipmentFile(int type, String name) {
            HackerFile file = new HackerFile(type);
            file.setLocation("");
            file.setName(name);
            java.util.HashMap content = new java.util.HashMap();
            content.put("attribute0", "0");
            content.put("quality0", "0");
            content.put("attribute1", "1");
            content.put("quality1", "0");
            file.setContent(content);
            return file;
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
