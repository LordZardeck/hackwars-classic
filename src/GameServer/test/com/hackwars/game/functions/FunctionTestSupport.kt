package com.hackwars.game.functions

import assignments.PacketAssignment
import com.hackwars.rpc.ChangeWatchPort
import com.hackwars.rpc.RequestZombieAttack
import game.ApplicationData
import game.ApplicationCommand
import game.ApplicationPayload
import game.Computer
import game.EquipmentSheet
import game.FileSystem
import game.NetworkSwitch
import game.WatchHandler
import game.payload.AddShowChoicesPayload
import game.payload.BooleanCommandPayload
import game.payload.DoChallengePayload
import game.payload.FloatCommandPayload
import game.payload.IntCommandPayload
import game.payload.LaunchNetworkAttackPayload
import game.payload.MapCommandPayload
import game.payload.RequestAttackDefaultPayload
import game.payload.RequestSavePayload
import game.payload.RequestTaskPayload
import game.payload.StringCommandPayload
import game.payload.TriggerWatchByIndexPayload
import game.payload.TriggerWatchByNotePayload
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object FunctionTestSupport {
    fun arrayCommand(function: String, vararg values: Any?): ApplicationData {
        return ApplicationData(ArrayCommandPayload(ApplicationCommand.of(function), arrayOf(*values)), 0, "source")
    }

    fun noArgsCommand(function: String, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(NullCommandPayload(ApplicationCommand.of(function)), port, sourceIp)
    }

    fun addShowChoices(vararg choices: Any?): ApplicationData {
        return ApplicationData(AddShowChoicesPayload(arrayOf(*choices)), 0, "source")
    }

    fun floatCommand(function: String, value: Float, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(FloatCommandPayload(ApplicationCommand.of(function), value), port, sourceIp)
    }

    fun intCommand(function: String, value: Int, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(IntCommandPayload(ApplicationCommand.of(function), value), port, sourceIp)
    }

    fun booleanCommand(function: String, value: Boolean, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(BooleanCommandPayload(ApplicationCommand.of(function), value), port, sourceIp)
    }

    fun stringCommand(function: String, value: String?, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(StringCommandPayload(ApplicationCommand.of(function), value), port, sourceIp)
    }

    fun mapCommand(function: String, value: HashMap<Any, Any>, port: Int = 0, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(MapCommandPayload(ApplicationCommand.of(function), value), port, sourceIp)
    }

    fun doChallenge(file: String, challengeId: String): ApplicationData {
        return ApplicationData(DoChallengePayload(file, challengeId), 0, "source")
    }

    fun changeWatchPort(watchId: Int, portId: Int): ApplicationData {
        return ApplicationData(ChangeWatchPort("source", watchId, portId), 0, "source")
    }

    fun requestAttackDefault(target: String, port: Int = 12, sourceIp: String = "7.7.7.7"): ApplicationData {
        return ApplicationData(RequestAttackDefaultPayload(target), port, sourceIp)
    }

    fun requestSave(fileName: String, triggerParameters: HashMap<Any, Any>?): ApplicationData {
        return ApplicationData(RequestSavePayload(fileName, triggerParameters), 0, "source")
    }

    fun requestTask(fileName: String?, questId: Int, taskName: String): ApplicationData {
        return ApplicationData(RequestTaskPayload(fileName, questId, taskName), 0, "source")
    }

    fun requestTrigger(index: Int, triggerParameters: HashMap<Any, Any>?, targetIp: String): ApplicationData {
        return ApplicationData(TriggerWatchByIndexPayload(index, triggerParameters, targetIp), 0, "source")
    }

    fun requestTriggerNote(note: String, triggerParameters: HashMap<Any, Any>?, targetIp: String): ApplicationData {
        return ApplicationData(TriggerWatchByNotePayload(note, triggerParameters, targetIp), 0, "source")
    }

    fun requestZombieAttack(parameters: RequestZombieAttack, port: Int = 22, sourceIp: String = "source"): ApplicationData {
        return ApplicationData(parameters, port, sourceIp)
    }

    fun launchNetworkAttack(npcIp: String): ApplicationData {
        return ApplicationData(LaunchNetworkAttackPayload(npcIp), 0, "source")
    }

    fun baseComputer(ip: String = "10.0.0.1"): Computer {
        val computer = mock<Computer>()
        whenever(computer.getIP()).thenReturn(ip)
        whenever(computer.getType()).thenReturn(Computer.PLAYER)
        whenever(computer.cpuType).thenReturn(0)
        whenever(computer.cpuLoad).thenReturn(0f)
        whenever(computer.getVoteCount()).thenReturn(0)
        whenever(computer.getPettyCash()).thenReturn(0f)
        whenever(computer.getDefaultBank()).thenReturn(11)
        whenever(computer.getDefaultAttack()).thenReturn(12)
        whenever(computer.getDefaultFTP()).thenReturn(13)
        whenever(computer.getDefaultHTTP()).thenReturn(14)
        whenever(computer.getDefaultShipping()).thenReturn(15)

        whenever(computer.stats).thenReturn(HashMap<Any?, Any?>())
        whenever(computer.showChoicesArray).thenReturn(ArrayList<Any?>())
        whenever(computer.currentQuests).thenReturn(HashMap<Any?, Any?>())
        whenever(computer.ports).thenReturn(HashMap<Any?, Any?>())

        val equipmentSheet = mock<EquipmentSheet>()
        whenever(equipmentSheet.cpuBonus).thenReturn(0f)
        computer.equipmentSheet = equipmentSheet

        whenever(computer.packetAssignment).thenReturn(mock<PacketAssignment>())
        whenever(computer.fileSystem).thenReturn(mock<FileSystem>())
        whenever(computer.watchHandler).thenReturn(mock<WatchHandler>())
        whenever(computer.computerHandler).thenReturn(mock<NetworkSwitch>())

        return computer
    }
}

@Suppress("ArrayInDataClass")
private data class ArrayCommandPayload(
    private val command: ApplicationCommand,
    val values: Array<Any?>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
}

private data class NullCommandPayload(
    private val command: ApplicationCommand
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = command
}
