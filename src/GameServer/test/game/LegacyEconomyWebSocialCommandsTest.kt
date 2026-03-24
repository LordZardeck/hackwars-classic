package game

import assignments.PacketAssignment
import com.hackwars.game.functions.FunctionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.HashMap
import game.payload.ContinuePurchasePayload
import game.payload.PettyCashDeltaPayload
import game.payload.PettyCashTransferPayload
import game.payload.RequestPurchasePayload
import game.payload.RequestWebPagePayload
import game.payload.SavePagePayload
import game.payload.SetPreferencesPayload
import game.payload.WebPagePayload

class LegacyEconomyWebSocialCommandsTest {
    @Test
    fun dispatchReturnsFalseForUnhandledCommand() {
        val fixture = TestFixture()
        try {
            val handled = fixture.handler.dispatch(
                fixture.computer,
                FunctionTestSupport.noArgsCommand("unhandled", sourceIp = fixture.computer.ip),
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
                ApplicationData(SetPreferencesPayload(preferences), 0, fixture.computer.ip),
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
                FunctionTestSupport.noArgsCommand("requestpage", sourceIp = fixture.computer.ip),
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
                ApplicationData(SavePagePayload("Title", builder.toString()), 0, fixture.computer.ip),
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
                ApplicationData(PettyCashTransferPayload(50.0f), 0, "2.2.2.2"),
                0
            )

            assertTrue(handled)
            assertEquals(75.0f, fixture.computer.pettyCash, 0.0001f)
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("2.2.2.2", dispatch.targetIp)
            assertEquals("message", dispatch.applicationData.command.wireName())
            assertTrue(dispatch.applicationData.payload is game.payload.StructuredMessagePayload)
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
                ApplicationData(PettyCashTransferPayload(50.0f, 50.0f, false), 0, "2.2.2.2"),
                0
            )

            assertTrue(handled)
            assertEquals(0.0f, fixture.computer.pettyCash, 0.0001f)
            assertEquals(2, fixture.dispatches.size)
            assertEquals("pettycash", fixture.dispatches[0].applicationData.command.wireName())
            assertTrue(fixture.dispatches[0].applicationData.payload is PettyCashDeltaPayload)
            assertEquals("message", fixture.dispatches[1].applicationData.command.wireName())
            assertTrue(fixture.dispatches[1].applicationData.payload is game.payload.StructuredMessagePayload)
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
                ApplicationData(RequestPurchasePayload("bundle.bin", 2), 0, "buyer-ip"),
                0
            )

            assertTrue(handled)
            assertEquals(1, offer.quantity)
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("buyer-ip", dispatch.targetIp)
            assertEquals("continuepurchase", dispatch.applicationData.command.wireName())
            val payload = dispatch.applicationData.payload as ContinuePurchasePayload
            val reserved = payload.file
            assertEquals("bundle.bin", reserved.name)
            assertEquals(2, reserved.quantity)
            assertEquals("store-revenue", payload.revenueTarget)
            assertEquals(fixture.computer.type, payload.sellerType)
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
            file.name = "guide.txt"
            file.setContent(HashMap<Any?, Any?>())
            file.price = 10.0f
            file.quantity = 1

            val handled = fixture.handler.dispatch(
                fixture.computer,
                ApplicationData(ContinuePurchasePayload(file, "seller-bank", 0), 0, "seller-site"),
                0
            )

            assertTrue(handled)
            assertEquals(5, fixture.dispatches.size)
            assertEquals("savefile", fixture.dispatches[0].applicationData.command.wireName())
            assertEquals(fixture.computer.ip, fixture.dispatches[0].targetIp)
            assertEquals("pettycash", fixture.dispatches[1].applicationData.command.wireName())
            assertEquals(fixture.computer.ip, fixture.dispatches[1].targetIp)
            assertEquals("pettycash", fixture.dispatches[2].applicationData.command.wireName())
            assertEquals("seller-bank", fixture.dispatches[2].targetIp)
            assertEquals("requestwebpage", fixture.dispatches[3].applicationData.command.wireName())
            assertEquals("seller-site", fixture.dispatches[3].targetIp)
            assertEquals("requestequipment", fixture.dispatches[4].applicationData.command.wireName())
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
                ApplicationData(RequestWebPagePayload(parameters), 0, "browser-ip"),
                0
            )

            assertTrue(handled)
            assertEquals(1, fixture.dispatches.size)
            val dispatch = fixture.dispatches[0]
            assertEquals("browser-ip", dispatch.targetIp)
            assertEquals("webpage", dispatch.applicationData.command.wireName())
            val payload = dispatch.applicationData.payload as WebPagePayload
            assertEquals("Server Not Found", payload.title)
            assertTrue(payload.body.contains("HTTP Status 408"))
            assertEquals(77, payload.packetId)
        } finally {
            fixture.close()
        }
    }

    private class TestFixture {
        val computer = Computer("10.0.0.1", null, -1, null)
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
            file.name = name
            file.quantity = quantity
            file.price = price
            file.maker = "Alexander"
            file.setContent(HashMap<Any?, Any?>())
            file.location = "Store/"
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
