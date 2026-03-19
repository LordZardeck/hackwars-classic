package game.computer.dispatch

import game.EquipmentSheet
import game.FileSystem
import game.HackerFile
import game.NetworkSwitch
import game.WatchHandler
import assignments.PacketAssignment

/**
 * Narrow bridge for the legacy run-loop command chain.
 *
 * The integrator can have `Computer` implement this interface later, or add
 * equivalent forwarding methods, so the giant `function.equals(...)` ladder
 * can move into handler files without direct package-private field access.
 */
interface RunLoopCommandContext {
    fun getIP(): String
    fun getType(): Int
    fun getLoaded(): Boolean
    fun getLoading(): Boolean

    fun getFileSystem(): FileSystem
    fun getWatchHandler(): WatchHandler
    fun getEquipmentSheet(): EquipmentSheet
    fun getComputerHandler(): NetworkSwitch
    fun getPacketAssignment(): PacketAssignment

    fun getPorts(): HashMap<*, *>
    fun getStats(): HashMap<*, *>
    fun getCurrentQuests(): HashMap<*, *>
    fun getMessages(): ArrayList<*>
    fun getDamage(): ArrayList<*>

    fun getPettyCash(): Float
    fun setPettyCash(pettyCash: Float)
    fun setBank(bankMoney: Float)
    fun setDailyPayReduction(dailyPayReduction: Float)
    fun getDailyPayReduction(): Float

    fun setDefaultBank(defaultBank: Int)
    fun setDefaultAttack(defaultAttack: Int)
    fun setDefaultShipping(defaultShipping: Int)
    fun setDefaultHTTP(defaultHTTP: Int)
    fun setDefaultFTP(defaultFTP: Int)
    fun setRepaired(repaired: Boolean)
    fun setGlobal(index: Int, data: Any?)

    fun addMessage(message: String)
    fun addMessage(message: Object?)
    fun addMessage(message: String, parameters: Object?)
    fun addMessage(message: Object?, parameters: Object?)
    fun addMessage(message: Object?, parameters: Object?, portInfo: Object?)

    fun checkBank(): Boolean
    fun checkFirewall(): Boolean
    fun checkShipping(): Boolean
    fun checkHTTP(): Boolean
    fun checkFTP(): Boolean
    fun checkQuest(id: Int): Boolean
    fun checkRename(file: HackerFile, path: String): HackerFile

    fun sendPacket()
    fun sendDamagePacket()
}
