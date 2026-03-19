package com.hackwars.game.functions

import assignments.PacketAssignment
import game.Computer
import game.EquipmentSheet
import game.FileSystem
import game.NetworkSwitch
import game.WatchHandler
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object FunctionTestSupport {
    fun baseComputer(ip: String = "10.0.0.1"): Computer {
        val computer = mock<Computer>()
        whenever(computer.ip).thenReturn(ip)
        whenever(computer.type).thenReturn(Computer.PLAYER)
        whenever(computer.cpuType).thenReturn(0)
        whenever(computer.cpuLoad).thenReturn(0f)
        whenever(computer.voteCount).thenReturn(0)
        whenever(computer.pettyCash).thenReturn(0f)
        whenever(computer.defaultBank).thenReturn(11)
        whenever(computer.defaultAttack).thenReturn(12)
        whenever(computer.defaultFTP).thenReturn(13)
        whenever(computer.defaultHTTP).thenReturn(14)
        whenever(computer.defaultShipping).thenReturn(15)

        whenever(computer.stats).thenReturn(HashMap<Any, Any>())
        whenever(computer.showChoicesArray).thenReturn(ArrayList<Any>())
        whenever(computer.currentQuests).thenReturn(HashMap<Any, Any>())
        whenever(computer.ports).thenReturn(HashMap<Any, Any>())

        val equipmentSheet = mock<EquipmentSheet>()
        whenever(equipmentSheet.cpuBonus).thenReturn(0f)
        whenever(computer.equipmentSheet).thenReturn(equipmentSheet)

        whenever(computer.packetAssignment).thenReturn(mock<PacketAssignment>())
        whenever(computer.fileSystem).thenReturn(mock<FileSystem>())
        whenever(computer.watchHandler).thenReturn(mock<WatchHandler>())
        whenever(computer.computerHandler).thenReturn(mock<NetworkSwitch>())

        return computer
    }
}
