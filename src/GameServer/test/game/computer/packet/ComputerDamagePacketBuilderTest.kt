package game.computer.packet

import assignments.DamageAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

class ComputerDamagePacketBuilderTest {
    @Test
    fun populate_setsTheDamagePayload() {
        val packet = DamageAssignment(0)

        val snapshot = ComputerDamagePacketSnapshot(
            attackXP = 15.0f,
            merchantXP = 30.0f,
            fireWallXP = 45.0f,
            watchXP = 60.0f,
            scanningXP = 75.0f,
            httpXP = 90.0f,
            redirectXP = 105.0f,
            repairXP = 120.0f,
            cpuCost = 31.5f,
            healthUpdates = listOf(
                PortHealthSnapshot(
                    portNumber = 11,
                    health = 90.0f,
                    cpuCost = 3.0f,
                    firewallType = "none",
                    healCount = 1,
                    baseCpuCostAndFirewall = 4.0f,
                    windowHandle = 101,
                ),
                PortHealthSnapshot(
                    portNumber = 22,
                    health = 80.0f,
                    cpuCost = 5.0f,
                    firewallType = mapOf("name" to "Shield"),
                    healCount = 2,
                    baseCpuCostAndFirewall = 6.0f,
                    windowHandle = 202,
                ),
            ),
            damageEntries = listOf(
                arrayOf(101, 20.0f, false, false),
                arrayOf(202, 12.5f, true, true, "miner"),
            ),
        )

        val populated = ComputerDamagePacketBuilder().populate(packet, snapshot)

        assertTrue(populated === packet)
        assertEquals(15.0f, packet.getAttackXP(), 0.0f)
        assertEquals(30.0f, packet.getMerchantXP(), 0.0f)
        assertEquals(45.0f, packet.getFireWallXP(), 0.0f)
        assertEquals(60.0f, packet.getWatchXP(), 0.0f)
        assertEquals(75.0f, packet.getScanningXP(), 0.0f)
        assertEquals(90.0f, packet.getHTTPXP(), 0.0f)
        assertEquals(105.0f, packet.getRedirectXP(), 0.0f)
        assertEquals(120.0f, packet.getRepairXP(), 0.0f)
        assertEquals(31.5f, packet.getCPUCost(), 0.0f)
        assertTrue(
            Arrays.deepEquals(
                arrayOf(
                    arrayOf(11, 90.0f, 3.0f, "none", 1, 4.0f, 101),
                    arrayOf(22, 80.0f, 5.0f, mapOf("name" to "Shield"), 2, 6.0f, 202),
                ),
                packet.getHealthUpdates(),
            )
        )
        assertTrue(
            Arrays.deepEquals(
                arrayOf(
                    arrayOf(101, 20.0f, false, false),
                    arrayOf(202, 12.5f, true, true, "miner"),
                ),
                packet.getDamage(),
            )
        )
    }
}
