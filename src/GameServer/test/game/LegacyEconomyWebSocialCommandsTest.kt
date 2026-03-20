package game

import assignments.PacketAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.HashMap
import util.Time

class LegacyEconomyWebSocialCommandsTest {
    @Test
    fun dispatchReturnsFalseForUnhandledCommand() {
        val fixture = TestFixture()
        try {
            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("unhandled", null, 0, fixture.computer.ip),
                0
            )

            assertFalse(handled)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun setPreferencesStoresProvidedPreferences() {
        val fixture = TestFixture()
        try {
            val preferences = HashMap<Any?, Any?>()
            preferences["music"] = true

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("setpreferences", arrayOf("ignored", preferences), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertSame(preferences, fixture.computer.preferences)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun requestPageCopiesSavedContentIntoThePacket() {
        val fixture = TestFixture()
        try {
            fixture.computer.PA = PacketAssignment(0)
            fixture.computer.pageTitle = "Welcome"
            fixture.computer.pageBody = "Hello"

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("requestpage", null, 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(fixture.computer.systemChange)
            assertEquals("Welcome", fixture.computer.PA.getTitle())
            assertEquals("Hello", fixture.computer.PA.getBody())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun savePageRejectsOversizedBody() {
        val fixture = TestFixture()
        try {
            val builder = StringBuilder(30001)
            repeat(30001) { builder.append('a') }

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("savepage", arrayOf("Title", builder.toString()), 0, fixture.computer.ip),
                0
            )

            assertTrue(handled)
            assertTrue(fixture.computer.systemChange)
            assertEquals("", fixture.computer.pageTitle)
            assertEquals("", fixture.computer.pageBody)
            assertFalse(fixture.computer.pageChanged)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun pettyCashTransferWithActiveBankCreditsCashAndNotifiesSender() {
        val fixture = TestFixture()
        try {
            fixture.addPort(11, Port.BANKING, true)
            fixture.seedSkillStats(100000.0f)
            fixture.computer.pettyCash = 25.0f

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("pettycash", 50.0f, 0, "2.2.2.2"),
                0
            )

            assertTrue(handled)
            assertEquals(75.0f, fixture.computer.pettyCash, 0.0001f)
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("2.2.2.2", dispatch.targetIp)
            assertEquals("message", dispatch.applicationData.getFunction())
            assertEquals(1, fixture.computer.getMessages()!!.size)
            assertTrue(fixture.latestMessageText().contains("Received transfer of $50.00 from 2.2.2.2."))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun pettyCashTransferWithoutBankReturnsFundsAndSendsFailure() {
        val fixture = TestFixture()
        try {
            fixture.seedSkillStats(100000.0f)
            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("pettycash", arrayOf(50.0f, 50.0f), 0, "2.2.2.2"),
                0
            )

            assertTrue(handled)
            assertEquals(0.0f, fixture.computer.pettyCash, 0.0001f)
            assertEquals(2, fixture.dispatches.size)
            assertEquals("pettycash", fixture.dispatches[0].applicationData.getFunction())
            assertEquals(50.0f, fixture.dispatches[0].applicationData.getParameters())
            assertEquals("message", fixture.dispatches[1].applicationData.getFunction())
            assertSame(MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT, fixture.dispatches[1].applicationData.getParameters())
            assertTrue(fixture.latestMessageText().contains("Could not recieve transfer of $50.00 from 2.2.2.2."))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun requestPurchaseReservesStoreInventoryAndQueuesContinuePurchase() {
        val fixture = TestFixture()
        try {
            fixture.computer.storeRevenueTarget = "store-revenue"
            val offer = fixture.addStoreFile("bundle.bin", 3, 15.0f)

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("requestpurchase", arrayOf("bundle.bin", 2), 0, "buyer-ip"),
                0
            )

            assertTrue(handled)
            assertEquals(1, offer.getQuantity())
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("buyer-ip", dispatch.targetIp)
            assertEquals("continuepurchase", dispatch.applicationData.getFunction())
            val payload = dispatch.applicationData.getParameters() as Array<*>
            val reserved = payload[0] as HackerFile
            assertEquals("bundle.bin", reserved.getName())
            assertEquals(2, reserved.getQuantity())
            assertEquals("store-revenue", payload[1])
            assertEquals(fixture.computer.type, payload[2])
        } finally {
            fixture.close()
        }
    }

    @Test
    fun continuePurchaseQueuesSaveTransfersAndRefreshesOnSuccessfulSoftwarePurchase() {
        val fixture = TestFixture()
        try {
            fixture.addPort(11, Port.BANKING, true)
            fixture.seedSkillStats(0.0f)
            fixture.computer.pettyCash = 500.0f

            val file = HackerFile(HackerFile.TEXT)
            file.setName("guide.txt")
            file.setContent(HashMap<Any?, Any?>())
            file.setPrice(10.0f)
            file.setQuantity(1)

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("continuepurchase", arrayOf(file, "seller-bank", 0), 0, "seller-site"),
                0
            )

            assertTrue(handled)
            assertEquals(5, fixture.dispatches.size)
            assertEquals("savefile", fixture.dispatches[0].applicationData.getFunction())
            assertEquals(fixture.computer.ip, fixture.dispatches[0].targetIp)
            assertEquals("pettycash", fixture.dispatches[1].applicationData.getFunction())
            assertEquals(fixture.computer.ip, fixture.dispatches[1].targetIp)
            assertEquals("pettycash", fixture.dispatches[2].applicationData.getFunction())
            assertEquals("seller-bank", fixture.dispatches[2].targetIp)
            assertEquals("requestwebpage", fixture.dispatches[3].applicationData.getFunction())
            assertEquals("seller-site", fixture.dispatches[3].targetIp)
            assertEquals("requestequipment", fixture.dispatches[4].applicationData.getFunction())
            assertEquals(fixture.computer.ip, fixture.dispatches[4].targetIp)
            assertTrue(fixture.latestMessageText().contains("You have successfully purchased 1 guide.txt"))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun requestWebpageWithoutHttpPortServesFallbackErrorPage() {
        val fixture = TestFixture()
        try {
            val parameters = HashMap<Any?, Any?>()
            parameters["packetid"] = 77

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData("requestwebpage", parameters, 0, "browser-ip"),
                0
            )

            assertTrue(handled)
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("browser-ip", dispatch.targetIp)
            assertEquals("webpage", dispatch.applicationData.getFunction())
            val payload = dispatch.applicationData.getParameters() as Array<*>
            assertEquals("Server Not Found", payload[0])
            assertTrue((payload[1] as String).contains("HTTP Status 408"))
            assertEquals(77, payload[3])
        } finally {
            fixture.close()
        }
    }

    private class TestFixture {
        val time = Time()
        val computer = Computer("10.0.0.1", null, time, -1, null)
        val handler = LegacyEconomyWebSocialCommands()
        val dispatches = ArrayList<CapturedDispatch>()

        init {
            stopComputerRuntime(computer)
            computer.connectionID = 1
            computer.systemChange = false
            computer.pageTitle = ""
            computer.pageBody = ""
            computer.pageChanged = false
            seedSkillStats(0.0f)
            computer.MyFileSystem.addDirectory("Store/")
            computer.MyFileSystem.addDirectory("Public/")
            computer.MyComputerHandler = CapturingNetworkSwitch(computer, dispatches)
        }

        fun close() {
            stopComputerRuntime(computer)
            time.clean()
        }

        fun addPort(number: Int, type: Int, on: Boolean) {
            val port = Port(computer, computer.getComputerHandler())
            port.setNumber(number)
            port.setType(type)
            port.setOn(on)
            port.setDummy(false)
            computer.Ports[number] = port
        }

        fun addStoreFile(name: String, quantity: Int, price: Float): HackerFile {
            val file = HackerFile(HackerFile.TEXT)
            file.setName(name)
            file.setQuantity(quantity)
            file.setPrice(price)
            file.setMaker("Alexander")
            file.setContent(HashMap<Any?, Any?>())
            file.setLocation("Store/")
            assertTrue(computer.MyFileSystem.addFile(file, true))
            return file
        }

        fun seedSkillStats(xp: Float) {
            computer.Stats["Attack"] = xp
            computer.Stats["Bank"] = xp
            computer.Stats["Watch"] = xp
            computer.Stats["Scanning"] = xp
            computer.Stats["Webdesign"] = xp
            computer.Stats["Redirecting"] = xp
            computer.Stats["Repair"] = xp
            computer.Stats["FireWall"] = xp
        }

        fun latestMessageText(): String {
            val messages = computer.getMessages()!!
            assertFalse(messages.isEmpty())
            val latest = messages[messages.size - 1] as Array<*>
            return latest[0] as String
        }
    }

    private data class CapturedDispatch(
        val applicationData: ApplicationData,
        val targetIp: String
    )

    private class CapturingNetworkSwitch(
        computer: Computer,
        private val dispatches: ArrayList<CapturedDispatch>
    ) : NetworkSwitch(computer, null) {
        override fun addData(AD: ApplicationData, ip: String?) {
            dispatches.add(CapturedDispatch(AD, ip!!))
        }
    }

    companion object {
        private fun stopComputerRuntime(computer: Computer) {
            computer.shutdown()
            computer.joinBlocking()
        }
    }
}
