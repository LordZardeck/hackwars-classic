package game.computer.packet

import assignments.DamageAssignment
import java.util.ArrayList

class ComputerDamagePacketBuilder {
    fun populate(
        packet: DamageAssignment,
        snapshot: ComputerDamagePacketSnapshot,
    ): DamageAssignment {
        packet.setAttackXP(snapshot.attackXP)
        packet.setMerchantXP(snapshot.merchantXP)
        packet.setFireWallXP(snapshot.fireWallXP)
        packet.setWatchXP(snapshot.watchXP)
        packet.setScanningXP(snapshot.scanningXP)
        packet.setHTTPXP(snapshot.httpXP)
        packet.setRedirectXP(snapshot.redirectXP)
        packet.setRepairXP(snapshot.repairXP)
        packet.setCPUCost(snapshot.cpuCost)

        val healthUpdates = ArrayList<Any>()
        snapshot.healthUpdates.forEach { healthUpdates.add(it.toPayload()) }
        packet.addHealthUpdate(healthUpdates)

        val damageEntries = ArrayList<Any>()
        snapshot.damageEntries.forEach { damageEntries.add(it) }
        packet.addDamage(damageEntries)

        return packet
    }
}
