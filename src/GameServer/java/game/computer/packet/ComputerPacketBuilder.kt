package game.computer.packet

import assignments.PacketAssignment
import java.util.ArrayList
import java.util.HashMap

class ComputerPacketBuilder {
    fun populate(
        packet: PacketAssignment,
        snapshot: ComputerStandardPacketSnapshot,
    ): PacketAssignment {
        packet.setPettyCash(snapshot.pettyCash)
        packet.setBankMoney(snapshot.bankMoney)
        packet.setCPUMax(snapshot.cpuMax)
        packet.setCPUType(snapshot.cpuType)
        packet.setMemoryType(snapshot.memoryType)
        packet.setDefaultBank(snapshot.defaultBank)
        packet.setDefaultAttack(snapshot.defaultAttack)
        packet.setDefaultHTTP(snapshot.defaultHttp)
        packet.setDefaultFTP(snapshot.defaultFtp)
        packet.setDefaultShipping(snapshot.defaultShipping)
        packet.setHackCount(snapshot.hackCount)
        packet.setVoteCount(snapshot.voteCount)
        packet.setCPUCost(snapshot.cpuCost)
        packet.setHDType(snapshot.hdType)
        packet.setHDQuantity(snapshot.hdQuantity)
        packet.setHDMaximum(snapshot.hdMaximum)
        packet.setServerLoad(snapshot.serverLoad)
        packet.setCommodities(snapshot.commodities)
        packet.setHealDiscount(snapshot.healDiscount)
        packet.setVotesLeft(snapshot.votesLeft)
        packet.setMessages(snapshot.messages)
        packet.setChoices(snapshot.choices.toTypedArray())

        snapshot.logMessages?.let { packet.addLogUpdate(ArrayList(it)) }
        snapshot.countdownSeconds?.let { packet.setCountDown(it) }
        snapshot.preferences?.let {
            val copiedPreferences = HashMap<String, Any?>()
            copiedPreferences.putAll(it)
            packet.setPreferences(copiedPreferences)
        }

        return packet
    }
}
