package game

import assignments.PacketWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import util.Time

class LegacyWatchEquipmentCommandsTest {
    @Test
    fun dispatch_returnsFalse_forNonOwnedCommand() {
        val fixture = TestFixture()
        try {
            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("not-owned", null, 0, fixture.computer.ip),
                0
            )

            assertFalse(handled)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dispatch_fetchWatches_populatesPacketAssignment() {
        val fixture = TestFixture()
        try {
            val watch = Watch(fixture.computer)
            watch.setType(Watch.PETTY_CASH)
            watch.setOn(true)
            watch.setNote("alarm")
            watch.setCPUCost(2.5f)
            watch.setSearchFireWall(3)
            watch.setQuantity(125.0f)
            watch.setPort(9)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.systemChange = false

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("fetchwatches", null, 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(fixture.computer.systemChange)
            val packetWatches: Array<PacketWatch> = fixture.computer.PA.getPacketWatches()
            assertEquals(1, packetWatches.size)
            assertEquals(Watch.PETTY_CASH, packetWatches[0].getType())
            assertEquals("alarm", packetWatches[0].getNote())
            assertTrue(packetWatches[0].getOn())
            assertEquals(9, packetWatches[0].getPort())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dispatch_changeWatchType_updatesWatchAndQueuesRefresh() {
        val fixture = TestFixture()
        try {
            val watch = Watch(fixture.computer)
            watch.setType(Watch.HEALTH)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.Tasks.clear()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("changewatchtype", arrayOf(0, Watch.SCAN), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertEquals(Watch.SCAN, watch.getType())
            assertQueuedFetchWatches(fixture)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dispatch_setWatchOnOff_turnsWatchOnAndQueuesRefreshWhenWithinLimits() {
        val fixture = TestFixture()
        try {
            val watch = Watch(fixture.computer)
            watch.setType(Watch.PETTY_CASH)
            watch.setCPUCost(3.0f)
            watch.setOn(false)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.Tasks.clear()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("setwatchonoff", arrayOf<Any>(0, true), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(watch.getOn())
            assertQueuedFetchWatches(fixture)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dispatch_setWatchObservedPorts_updatesObservedPortsAndQueuesRefresh() {
        val fixture = TestFixture()
        try {
            val watch = Watch(fixture.computer)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.Tasks.clear()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("setwatchobservedports", arrayOf<Any>(0, arrayOf(3, 7, 9)), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertEquals(3, watch.getObservedPorts().size)
            assertEquals(3, watch.getObservedPorts()[0])
            assertEquals(7, watch.getObservedPorts()[1])
            assertEquals(9, watch.getObservedPorts()[2])
            assertQueuedFetchWatches(fixture)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dispatch_requestEquipment_populatesPrimaryAndSecondaryDirectories() {
        val fixture = TestFixture()
        try {
            val agpCard = fixture.createEquipmentFile(HackerFile.AGP, "Alpha AGP")
            fixture.computer.MyFileSystem.addFile(agpCard, false)
            fixture.computer.systemChange = false

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("requestequipment", Integer.valueOf(13), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(fixture.computer.systemChange)
            assertNotNull(fixture.computer.PA.getDirectory())
            assertEquals(13, fixture.computer.PA.getDirectory()[0])
            assertNotNull(fixture.computer.PA.getSecondaryDirectory())
            assertEquals(13, fixture.computer.PA.getSecondaryDirectory()[0])
            val returned = fixture.computer.PA.getDirectory()[1] as HackerFile
            assertEquals("Alpha AGP", returned.getName())
            assertNotNull(returned.getContent()["bonusdata"])
        } finally {
            fixture.close()
        }
    }

    private fun assertQueuedFetchWatches(fixture: TestFixture) {
        assertEquals(1, fixture.computer.Tasks.size)
        val queued = fixture.computer.Tasks[0] as ApplicationData
        assertEquals("fetchwatches", queued.function)
    }

    private class TestFixture {
        val time = Time()
        val computer = Computer("10.0.0.1", null, time, -1, null)
        val handler = LegacyWatchEquipmentCommands()

        init {
            stopComputerThread(computer)
            computer.Tasks.clear()
        }

        fun close() {
            stopComputerThread(computer)
            time.clean()
        }

        fun createEquipmentFile(type: Int, name: String): HackerFile {
            val file = HackerFile(type)
            file.setLocation("")
            file.setName(name)
            val content = HashMap<Any?, Any?>()
            content["attribute0"] = "0"
            content["quality0"] = "0"
            content["attribute1"] = "1"
            content["quality1"] = "0"
            file.setContent(content)
            return file
        }
    }

    companion object {
        private fun stopComputerThread(computer: Computer) {
            computer.run = false
            val thread = computer.MyThread
            if (thread != null) {
                thread.interrupt()
                try {
                    thread.join(200)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            computer.MyThread = null
        }
    }
}
