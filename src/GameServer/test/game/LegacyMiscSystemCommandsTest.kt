package game

import assignments.PacketAssignment
import com.hackwars.rpc.ClueData
import com.hackwars.rpc.HacktendoActivate
import com.hackwars.rpc.HacktendoTarget
import com.hackwars.rpc.RequestDirectory
import com.hackwars.rpc.Unlock
import game.payload.BountyHttpPayload
import game.payload.PingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.util.ArrayList
import java.util.HashMap

class LegacyMiscSystemCommandsTest {
    private val handler = LegacyMiscSystemCommands()

    @Test
    fun dispatch_returnsFalse_forNonOwnedCommand() {
        val computer = baseComputer()

        val handled = handler.dispatch(computer, ApplicationData(RequestDirectory("10.0.0.1", null), 0, "10.0.0.1"), 0)

        assertFalse(handled)
    }

    @Test
    fun dispatch_bountyhttp_updatesLastBountyHttp() {
        val computer = baseComputer()

        val handled = handler.dispatch(computer, ApplicationData(BountyHttpPayload("12.13.14.15"), 0, "10.0.0.1"), 0)

        assertTrue(handled)
        assertEquals("12.13.14.15", computer.lastBountyHTTPIP)
    }

    @Test
    fun dispatch_unlock_withMatchingCode_clearsLockedState() {
        val computer = baseComputer()
        computer.unlockKey = "alpha"
        computer.locked = true
        computer.lockCount = 42
        computer.RESEND_CAPTCHA = false

        val handled = handler.dispatch(computer, ApplicationData(Unlock("10.0.0.1", "alpha"), 0, "10.0.0.1"), 0)

        assertTrue(handled)
        assertFalse(computer.locked)
        assertEquals(0, computer.lockCount)
        assertFalse(computer.RESEND_CAPTCHA)
    }

    @Test
    fun dispatch_unlock_withWrongCode_requestsCaptchaResend() {
        val computer = baseComputer()
        computer.unlockKey = "alpha"
        computer.locked = true
        computer.lockCount = 42
        computer.RESEND_CAPTCHA = false

        val handled = handler.dispatch(computer, ApplicationData(Unlock("10.0.0.1", "beta"), 0, "10.0.0.1"), 0)

        assertTrue(handled)
        assertTrue(computer.locked)
        assertEquals(42, computer.lockCount)
        assertTrue(computer.RESEND_CAPTCHA)
    }

    @Test
    fun dispatch_ping_isHandledWithoutMutatingPingTimestamp() {
        val computer = baseComputer()
        computer.lastPingTime = 777L

        val handled = handler.dispatch(computer, ApplicationData(PingPayload, 0, "10.0.0.1"), 0)

        assertTrue(handled)
        assertEquals(777L, computer.lastPingTime)
    }

    @Test
    fun dispatch_hacktendoCommands_areHandledAsNoOps() {
        val computer = baseComputer()

        val targetHandled = handler.dispatch(
            computer,
            ApplicationData(HacktendoTarget(1, 2, "10.0.0.1", 3, 4), 0, "10.0.0.1"),
            0
        )
        val activateHandled = handler.dispatch(
            computer,
            ApplicationData(HacktendoActivate(5, 6, "10.0.0.1"), 0, "10.0.0.1"),
            0
        )
        val clueHandled = handler.dispatch(
            computer,
            ApplicationData(ClueData("10.0.0.1", "payload"), 0, "10.0.0.1"),
            0
        )

        assertTrue(targetHandled)
        assertTrue(activateHandled)
        assertTrue(clueHandled)
    }

    private fun baseComputer(): Computer {
        val computer = Mockito.mock(Computer::class.java, Answers.CALLS_REAL_METHODS)
        computer.ip = "10.0.0.1"
        computer.userName = "tester"
        computer.connectionID = -1
        computer.systemChange = false
        computer.Messages = ArrayList()
        computer.Damage = ArrayList()
        computer.CurrentQuests = HashMap()
        computer.Stats = HashMap()
        computer.PA = PacketAssignment(0)
        computer.equipmentSheet = Mockito.mock(EquipmentSheet::class.java)
        computer.messageHandler = MessageHandler(computer)
        computer.MyFileSystem = FileSystem(computer)
        return computer
    }
}
