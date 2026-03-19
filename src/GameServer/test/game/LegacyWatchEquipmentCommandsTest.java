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
            Assert.assertEquals(1, fixture.computer.Tasks.size());
            ApplicationData queued = (ApplicationData) fixture.computer.Tasks.get(0);
            Assert.assertEquals("fetchwatches", queued.getFunction());
        } finally {
            fixture.close();
        }
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
