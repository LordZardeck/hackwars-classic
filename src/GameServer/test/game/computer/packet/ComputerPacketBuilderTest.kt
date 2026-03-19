package game.computer.packet

import assignments.PacketAssignment
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

class ComputerPacketBuilderTest {
    @Test
    fun populate_setsTheStandardPacketPayload_withoutTouchingExistingFlags() {
        val packet = PacketAssignment(0)
        packet.setRequestPrimary(true, 99)
        packet.setRequestSecondary(true, 77)

        val snapshot = ComputerStandardPacketSnapshot(
            pettyCash = 123.45f,
            bankMoney = 67.89f,
            cpuMax = 250.0f,
            cpuType = 3,
            memoryType = 4,
            defaultBank = 11,
            defaultAttack = 12,
            defaultHttp = 13,
            defaultFtp = 14,
            defaultShipping = 15,
            hackCount = 7,
            voteCount = 9,
            cpuCost = 31.5f,
            hdType = 2,
            hdQuantity = 8,
            hdMaximum = 100,
            serverLoad = 42,
            commodities = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f),
            healDiscount = 0.75f,
            votesLeft = 3,
            messages = arrayOf("first", "second"),
            choices = listOf(
                arrayOf("choice-a", 1),
                arrayOf("choice-b", true),
            ),
            logMessages = listOf(
                arrayOf("log one", "ip1"),
                arrayOf("log two", "ip2"),
            ),
            countdownSeconds = 12,
            preferences = mapOf(
                "theme" to "retro",
                "sound" to "on",
            ),
        )

        val populated = ComputerPacketBuilder().populate(packet, snapshot)

        assertTrue(populated === packet)
        assertEquals(123.45f, packet.pettyCash, 0.0f)
        assertEquals(67.89f, packet.bankMoney, 0.0f)
        assertEquals(250.0f, packet.getCPUMax(), 0.0f)
        assertEquals(3, packet.getCPUType())
        assertEquals(4, packet.getMemoryType())
        assertEquals(11, packet.getDefaultBank())
        assertEquals(12, packet.getDefaultAttack())
        assertEquals(13, packet.getDefaultHTTP())
        assertEquals(14, packet.getDefaultFTP())
        assertEquals(15, packet.getDefaultShipping())
        assertEquals(7, packet.getHackCount())
        assertEquals(9, packet.getVoteCount())
        assertEquals(31.5f, packet.getCPUCost(), 0.0f)
        assertEquals(2, packet.getHDType())
        assertEquals(8, packet.getHDQuantity())
        assertEquals(100, packet.getHDMaximum())
        assertEquals(42, packet.getServerLoad())
        assertArrayEquals(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f), packet.getCommodities(), 0.0f)
        assertEquals(0.75f, packet.getHealDiscount(), 0.0f)
        assertEquals(3, packet.getVotesLeft())
        assertArrayEquals(arrayOf("first", "second"), packet.getMessages())
        assertTrue(
            Arrays.deepEquals(
                arrayOf(
                    arrayOf("choice-a", 1),
                    arrayOf("choice-b", true),
                ),
                packet.getChoices(),
            )
        )
        assertArrayEquals(arrayOf("log one", "log two"), packet.getLogUpdate())
        assertEquals(12, packet.getCountDown())
        assertEquals(mapOf("theme" to "retro", "sound" to "on"), packet.getPreferences())
        assertTrue(packet.requestPrimary())
        assertEquals(99, packet.getRequestPrimaryID())
        assertTrue(packet.requestSecondary())
        assertEquals(77, packet.getRequestSecondaryID())
    }
}
