package game

import assignments.PacketAssignment
import com.hackwars.game.program.Program
import java.util.HashMap

// Compatibility shims for Kotlin call sites that still use Java-style getters/setters.

fun Computer.getType(): Int = type
fun Computer.getStats(): HashMap<Any?, Any?> = Stats
fun Computer.getEquipmentSheet(): EquipmentSheet = equipmentSheet
fun Computer.getComputerHandler(): NetworkSwitch = computerHandler
fun Computer.getFileSystem(): FileSystem = fileSystem
fun Computer.getWatchHandler(): WatchHandler = watchHandler
fun Computer.getCurrentTime(): Long = currentTime
fun Computer.getMaximumCPULoad(): Float = maximumCPULoad
fun Computer.getMaximumCPUNoBonus(): Float = maximumCPUNoBonus
fun Computer.getMaximumWatches(): Int = maximumWatches
fun Computer.getMaximumWatchesNoBonus(): Int = maximumWatchesNoBonus
fun Computer.getBaseCPULoad(): Float = baseCPULoad
fun Computer.getAttacking(): Boolean = attacking
fun Computer.getLogs(): String = logs
fun Computer.getNoobSafety(): Int = noobSafety
fun Computer.getRepairLevel(): Float = repairLevel
fun Computer.getHTTPLevel(): Float = hTTPLevel
fun Computer.getLastBountyHTTPIP(): String? = lastBountyHTTPIP

val Computer.NPC: Int
    get() = Computer.NPC

val Computer.commodityString: Array<String>
    get() = Computer.commodityString

val Computer.requiredRepairLevel: IntArray
    get() = Computer.requiredRepairLevel

fun Port.getIP(): String = IP
fun Port.getMyComputer(): Computer = myComputer
fun Port.getType(): Int = type
fun Port.setType(value: Int) {
    type = value
}
fun Port.getNumber(): Int = number
fun Port.setNumber(value: Int) {
    number = value
}
fun Port.getNote(): String? = note
fun Port.setNote(value: String?) {
    note = value
}
fun Port.getMaliciousTarget(): String? = maliciousTarget
fun Port.setMaliciousTarget(value: String?) {
    maliciousTarget = value
}
fun Port.getOn(): Boolean = on
fun Port.setOn(value: Boolean) {
    on = value
}
fun Port.getAttacking(): Boolean = attacking
fun Port.setAttacking(value: Boolean) {
    attacking = value
}
fun Port.getOverHeated(): Boolean = overHeated
fun Port.setOverHeated(value: Boolean) {
    overHeated = value
}
fun Port.getDummy(): Boolean = dummy
fun Port.setDummy(value: Boolean) {
    dummy = value
}
fun Port.getProgram(): Program? = program
fun Port.setProgram(value: Program?) {
    program = value
}
fun Port.getFireWall(): NewFireWall? = fireWall
fun Port.getHealth(): Float = health
fun Port.setHealth(value: Float) {
    health = value
}
fun Port.getAccessing(): String = accessing
fun Port.getTargetHP(): Float = targetHP
fun Port.setTargetHP(value: Float) {
    targetHP = value
}
fun Port.getTargetPettyCash(): Float = targetPettyCash
fun Port.setTargetPettyCash(value: Float) {
    targetPettyCash = value
}
fun Port.getTargetCPUCost(): Float = targetCPUCost
fun Port.setTargetCPUCost(value: Float) {
    targetCPUCost = value
}
fun Port.getTargetWatch(): Boolean = targetWatch
fun Port.setTargetWatch(value: Boolean) {
    targetWatch = value
}
fun Port.getCurrentPacket(): PacketAssignment? = currentPacket
fun Port.setCurrentPacket(value: PacketAssignment?) {
    currentPacket = value
}
fun Port.getLastDamageWindowHandle(): Int = lastDamageWindowHandle
fun Port.getLastDamageIP(): String = lastDamageIP
fun Port.getWindowHandle(): Int = windowHandle

val Port.actualCPUCost: Float
    get() = getActualCPUCost()

val Port.ip: String
    get() = getIP()
