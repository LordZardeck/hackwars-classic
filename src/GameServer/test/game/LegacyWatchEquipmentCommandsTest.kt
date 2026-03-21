package game

import assignments.PacketWatch
import com.hackwars.rpc.ChangeWatchType
import com.hackwars.rpc.FetchPorts
import com.hackwars.rpc.FetchWatches
import com.hackwars.rpc.SetWatchOnOff
import com.hackwars.rpc.SetWatchObservedPorts
import game.payload.RequestEquipmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWatchEquipmentCommandsTest {
    @Test
    fun dispatch_returnsFalse_forNonOwnedCommand() {
        val fixture = TestFixture()
        try {
            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(FetchPorts(fixture.computer.ip), 0, fixture.computer.ip),
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
            watch.type = (Watch.PETTY_CASH)
            watch.on = (true)
            watch.note = ("alarm")
            watch.actualCpuCost = (2.5f)
            watch.searchFireWall = (3)
            watch.quantity = (125.0f)
            watch.port = (9)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.systemChange = false

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(FetchWatches(fixture.computer.ip), 0, fixture.computer.ip),
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
            watch.type = (Watch.HEALTH)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.clearPendingTasks()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(ChangeWatchType(fixture.computer.ip, 0, Watch.SCAN), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertEquals(Watch.SCAN, watch.type)
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
            watch.type = (Watch.PETTY_CASH)
            watch.actualCpuCost = (3.0f)
            watch.on = (false)
            fixture.computer.MyWatchHandler.addWatch(watch)
            fixture.computer.clearPendingTasks()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(SetWatchOnOff(fixture.computer.ip, 0, true), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(watch.on)
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
            fixture.computer.clearPendingTasks()

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(SetWatchObservedPorts(fixture.computer.ip, 0, arrayOf(3, 7, 9)), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertEquals(3, watch.observedPorts.size)
            assertEquals(3, watch.observedPorts[0])
            assertEquals(7, watch.observedPorts[1])
            assertEquals(9, watch.observedPorts[2])
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
                ApplicationData(RequestEquipmentPayload(13), 0, fixture.computer.ip),
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
        val queuedTasks = fixture.computer.snapshotPendingTasks()
        assertEquals(1, queuedTasks.size)
        val queued = queuedTasks[0] as ApplicationData
        assertEquals("fetchwatches", queued.command.wireName())
    }

    private class TestFixture {
        val computer = Computer("10.0.0.1", null, -1, null)
        val handler = LegacyWatchEquipmentCommands()

        init {
            stopComputerRuntime(computer)
            computer.clearPendingTasks()
        }

        fun close() {
            stopComputerRuntime(computer)
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
        private fun stopComputerRuntime(computer: Computer) {
            computer.shutdown()
            computer.joinBlocking()
        }
    }
}
