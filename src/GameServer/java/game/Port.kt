package game

import assignments.PacketAssignment
import assignments.PacketPort
import com.hackwars.rpc.SaveFile
import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.Banking
import com.hackwars.game.program.Program
import com.hackwars.game.program.ShippingProgram
import game.payload.*
import java.text.NumberFormat
import java.util.HashMap
import kotlin.jvm.JvmName

class Port(
    @get:JvmName("getMyComputerProperty")
    val myComputer: Computer,
    private val myComputerHandler: NetworkSwitch
) {
    private val installScriptCommand = AttackInstallScriptPayload(null, null).getCommand()
    private val codeCommand = ApplicationCommand.of("code")

    private var currentRedirectXP = 0.0f
    private var currentRedirectIP = ""

    private var _type = 0
    @get:JvmName("getTypeProperty")
    @set:JvmName("setTypeProperty")
    var type: Int
        get() = _type
        set(value) {
            _type = value
        }

    private var _number = 0
    @get:JvmName("getNumberProperty")
    @set:JvmName("setNumberProperty")
    var number: Int
        get() = _number
        set(value) {
            _number = value
        }

    private var _note: String? = ""
    @get:JvmName("getNoteProperty")
    @set:JvmName("setNoteProperty")
    var note: String?
        get() = _note
        set(value) {
            _note = value
        }

    private var _maliciousTarget: String? = ""
    @get:JvmName("getMaliciousTargetProperty")
    @set:JvmName("setMaliciousTargetProperty")
    var maliciousTarget: String?
        get() = _maliciousTarget
        set(value) {
            _maliciousTarget = value
        }

    private var _on = false
    @get:JvmName("getOnProperty")
    @set:JvmName("setOnProperty")
    var on: Boolean
        get() = _on
        set(value) {
            _on = value
        }

    private var _attacking = false
    @get:JvmName("getAttackingProperty")
    @set:JvmName("setAttackingProperty")
    var attacking: Boolean
        get() = _attacking
        set(value) {
            _attacking = value
            myComputer.getDamage().add(arrayOf(windowHandle, value))
            myComputer.sendDamagePacket()
        }

    private var _overHeated = false
    @get:JvmName("getOverHeatedProperty")
    @set:JvmName("setOverHeatedProperty")
    var overHeated: Boolean
        get() = _overHeated
        set(value) {
            if (!_overHeated && value && attacking) {
                cancelAttack(false)
            } else if (_overHeated && !value) {
                resetPort(true, false)
            }
            _overHeated = value
        }

    private var freeze = false
    private var freezeStart = 0L
    private var _weakened = false

    @get:JvmName("getWeakenedProperty")
    val weakened: Boolean
        get() = _weakened

    private var _dummy = false
    @get:JvmName("getDummyProperty")
    @set:JvmName("setDummyProperty")
    var dummy: Boolean
        get() = _dummy
        set(value) {
            _dummy = value
        }

    private var _program: Program? = null
    @get:JvmName("getProgramProperty")
    @set:JvmName("setProgramProperty")
    var program: Program?
        get() = _program
        set(value) {
            _program = value
        }

    private var _fireWall: NewFireWall? = null
    @get:JvmName("getFireWallProperty")
    @set:JvmName("setFireWallProperty")
    var fireWall: NewFireWall?
        get() = _fireWall
        set(value) {
            _fireWall = value
        }

    private var _health = 100.0f
    private var healthSet = false
    @get:JvmName("getHealthProperty")
    @set:JvmName("setHealthProperty")
    var health: Float
        get() = _health
        set(value) {
            _health = value
            healthSet = true
        }

    private var maximumHealth = 100.0f

    private var _cpuCost = 0.0f
    @get:JvmName("getCpuCostProperty")
    @set:JvmName("setCpuCostProperty")
    var cpuCost: Float
        get() = getCPUCost()
        set(value) {
            setCPUCost(value)
        }

    private var _accessing = ""
    @get:JvmName("getAccessingProperty")
    val accessing: String
        get() = _accessing
    private var accessingPort = 0
    private var lastAccessed = 0L

    private var _targetHP = 100.0f
    @get:JvmName("getTargetHPProperty")
    @set:JvmName("setTargetHPProperty")
    var targetHP: Float
        get() = _targetHP
        set(value) {
            _targetHP = value
        }

    private var _targetPettyCash = 0.0f
    @get:JvmName("getTargetPettyCashProperty")
    @set:JvmName("setTargetPettyCashProperty")
    var targetPettyCash: Float
        get() = _targetPettyCash
        set(value) {
            _targetPettyCash = value
        }

    private var _targetCPUCost = 0.0f
    @get:JvmName("getTargetCPUCostProperty")
    @set:JvmName("setTargetCPUCostProperty")
    var targetCPUCost: Float
        get() = _targetCPUCost
        set(value) {
            _targetCPUCost = value
        }

    private var _targetWatch = false
    @get:JvmName("getTargetWatchProperty")
    @set:JvmName("setTargetWatchProperty")
    var targetWatch: Boolean
        get() = _targetWatch
        set(value) {
            _targetWatch = value
        }

    private var _currentPacket: PacketAssignment? = null
    @get:JvmName("getCurrentPacketProperty")
    @set:JvmName("setCurrentPacketProperty")
    var currentPacket: PacketAssignment?
        get() = _currentPacket
        set(value) {
            _currentPacket = value
        }

    private var healCount = 0

    private var _lastDamageWindowHandle = 0
    @get:JvmName("getLastDamageWindowHandleProperty")
    val lastDamageWindowHandle: Int
        get() = _lastDamageWindowHandle

    private var _lastDamageIP = ""
    @get:JvmName("getLastDamageIPProperty")
    val lastDamageIP: String
        get() = _lastDamageIP

    @get:JvmName("getIpProperty")
    val ip: String
        get() = myComputer.ip

    @get:JvmName("getIPProperty")
    val IP: String
        get() = ip

    @get:JvmName("getActualCPUCostProperty")
    val actualCPUCost: Float
        get() = getActualCPUCost()

    init {
        if (BANKING != PortType.BANKING.getCode() ||
            FTP != PortType.FTP.getCode() ||
            ATTACK != PortType.ATTACK.getCode() ||
            HTTP != PortType.HTTP.getCode() ||
            REDIRECT != PortType.REDIRECT.getCode() ||
            SHIPPING != PortType.SHIPPING.getCode()
        ) {
            throw IllegalStateException("Port constants are out of sync with PortType codes")
        }
    }

    fun getMyComputer(): Computer = myComputer
    fun getIP(): String = ip
    fun getNumber(): Int = number
    fun setNumber(number: Int) {
        this.number = number
    }
    fun getType(): Int = type
    fun setType(type: Int) {
        this.type = type
    }
    fun getNote(): String? = note
    fun setNote(note: String?) {
        this.note = note
    }
    fun getMaliciousTarget(): String? = maliciousTarget
    fun setMaliciousTarget(maliciousTarget: String?) {
        this.maliciousTarget = maliciousTarget
    }
    fun getOn(): Boolean = on
    fun setOn(on: Boolean) {
        this.on = on
    }
    fun getAttacking(): Boolean = attacking
    fun setAttacking(attacking: Boolean) {
        this.attacking = attacking
    }
    fun getOverHeated(): Boolean = overHeated
    fun setOverHeated(overHeated: Boolean) {
        this.overHeated = overHeated
    }
    fun getDummy(): Boolean = dummy
    fun setDummy(dummy: Boolean) {
        this.dummy = dummy
    }
    fun getProgram(): Program? = program
    fun setProgram(program: Program?) {
        this.program = program
    }
    fun getFireWall(): NewFireWall = fireWall!!
    fun setFireWall(fireWall: NewFireWall?) {
        this.fireWall = fireWall
    }
    fun setFireWall(newFireWall: HackerFile?) {
        fireWall!!.loadHackerFile(newFireWall)
    }
    fun getHealth(): Float = health
    fun setHealth(health: Float) {
        this.health = health
    }
    fun getAccessing(): String = accessing
    fun getAccessingPort(): Int = accessingPort
    fun getWeakened(): Boolean = weakened
    fun getTargetHP(): Float = targetHP
    fun setTargetHP(targetHP: Float) {
        this.targetHP = targetHP
    }
    fun getTargetPettyCash(): Float = targetPettyCash
    fun setTargetPettyCash(targetPettyCash: Float) {
        this.targetPettyCash = targetPettyCash
    }
    fun getTargetCPUCost(): Float = targetCPUCost
    fun setTargetCPUCost(targetCPUCost: Float) {
        this.targetCPUCost = targetCPUCost
    }
    fun getTargetWatch(): Boolean = targetWatch
    fun setTargetWatch(targetWatch: Boolean) {
        this.targetWatch = targetWatch
    }
    fun getCurrentPacket(): PacketAssignment = currentPacket!!
    fun setCurrentPacket(PA: PacketAssignment?) {
        currentPacket = PA
    }
    fun getHealCount(): Int = healCount
    fun getLastDamageWindowHandle(): Int = lastDamageWindowHandle
    fun getLastDamageIP(): String = lastDamageIP

    fun setMaximumHealth(maximumHealth: Float) {
        this.maximumHealth = maximumHealth
    }

    fun damagePort(damage: Float): Boolean {
        var returnMe = false
        if (_health < 100.0f) {
            returnMe = true
        }
        _health -= damage
        if (_health < 0.0f) {
            _health = 0.0f
        }
        if (_health >= maximumHealth) {
            _health = maximumHealth
        }

        if (myComputer.currentTime - freezeStart > FREEZE_TIME) {
            freeze = false
        }

        if (healthSet) {
            healthSet = false
            returnMe = true
        }

        return returnMe
    }

    fun getBaseCPUCost(): Float = _cpuCost

    fun getCPUCost(): Float {
        if (!on) {
            return 0.0f
        }

        var mult = 0.0f
        if ((type == ATTACK || type == REDIRECT) && (attacking || overHeated)) {
            mult += (maximumHealth - _health) * (myComputer.maximumCPULoad / 100.0f)
        }

        return if (dummy) {
            (_cpuCost + mult + fireWall!!.getCPUCost()) / 2.0f
        } else {
            _cpuCost + mult + fireWall!!.getCPUCost()
        }
    }

    fun getBaseCPUCostTotal(): Float {
        if (!on) {
            return 0.0f
        }

        return if (dummy) {
            (_cpuCost + fireWall!!.getCPUCost()) / 2.0f
        } else {
            _cpuCost + fireWall!!.getCPUCost()
        }
    }

    fun getBaseCPUCostAndFirewall(): Float {
        return if (dummy) {
            (_cpuCost + fireWall!!.getCPUCost()) / 2.0f
        } else {
            _cpuCost + fireWall!!.getCPUCost()
        }
    }

    fun getActualCPUCost(): Float {
        var mult = 0.0f
        if ((type == ATTACK || type == REDIRECT) && (attacking || overHeated)) {
            mult += (maximumHealth - _health) * (myComputer.maximumCPULoad / 100.0f)
        }

        return if (dummy) {
            (_cpuCost + mult + fireWall!!.getCPUCost()) / 2.0f
        } else {
            _cpuCost + mult + fireWall!!.getCPUCost()
        }
    }

    fun setCPUCost(cpuCost: Float) {
        this._cpuCost = cpuCost
    }

    fun cancelAttack(overHeated: Boolean) {
        if (program != null) {
            if (program is AttackProgram) {
                (program as AttackProgram).cancelAttack(overHeated)
            } else if (program is ShippingProgram) {
                (program as ShippingProgram).cancelAttack(overHeated)
            }
        }
    }

    fun checkTimeOut(currentTime: Long) {
        if (currentTime - lastAccessed > timeOut && weakened) {
            resetPort(true, true)
        } else if (currentTime - lastAccessed > fullTimeOut) {
            resetPort(true, true)
        }
    }

    fun setLastAccessed(lastAccessed: Long) {
        this.lastAccessed = lastAccessed
    }

    fun finalizeAllowed(MyApplicationData: ApplicationData): Boolean {
        var allowed = true
        if (weakened) {
            if (!dummy) {
            } else {
                allowed = false
                myComputerHandler.addData(
                    structuredMessage(
                        arrayOf(MessageHandler.PORT_WAS_DUMMY),
                        arrayOf(number, ip),
                        arrayOf(MyApplicationData.sourcePort, MyApplicationData.sourceIP)
                    ),
                    MyApplicationData.getSourceIP()
                )
            }
        } else {
            allowed = false
            myComputerHandler.addData(
                structuredMessage(
                    arrayOf(MessageHandler.PORT_WAS_NOT_WEAKENED),
                    arrayOf(number, ip),
                    arrayOf(MyApplicationData.sourcePort, MyApplicationData.sourceIP)
                ),
                MyApplicationData.getSourceIP()
            )
        }
        return allowed
    }

    fun resetPort(heal: Boolean, setAccessed: Boolean) {
        if (heal) {
            health = maximumHealth
        }
        lastAccessed = myComputer.currentTime
        _weakened = false
        if (setAccessed) {
            _accessing = ""
        }
        healCount = 0
    }

    fun resetHealCounter() {
        healCount = 0
    }

    val windowHandle: Int
        get() {
            var windowHandle = 0
            if (program is AttackProgram) {
                windowHandle = (program as AttackProgram).getWindowHandle()
            } else if (program is ShippingProgram) {
                windowHandle = (program as ShippingProgram).getWindowHandle()
            }
            return windowHandle
        }

    fun addApplicationData(MyApplicationData: ApplicationData, currentTime: Long) {
        val now = myComputer.currentTime
        val command = MyApplicationData.command
        if (on) {
            if (command == HEAL_COMMAND) {
                if (!myComputer.checkBank()) {
                    myComputerHandler.addData(textMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND), MyApplicationData.getSourceIP())
                } else if (overHeated) {
                    myComputerHandler.addData(textMessage(MessageHandler.HEAL_FAIL_OVERHEATED), MyApplicationData.getSourceIP())
                } else if (healCount > myComputer.HEAL_LIMIT) {
                    myComputerHandler.addData(
                        structuredMessage(arrayOf(MessageHandler.HEAL_FAIL_LIMIT), arrayOf(myComputer.HEAL_LIMIT + 1)),
                        MyApplicationData.getSourceIP()
                    )
                } else if (!weakened) {
                    val cost = (maximumHealth - health) * 2.0f * myComputer.equipmentSheet.getHealBonus()
                    if (myComputer.getPettyCash() >= cost) {
                        healCount++
                        myComputerHandler.addData(ApplicationData(PettyCashDeltaPayload(cost * -1.0f), 0, ip), ip)
                        health = maximumHealth
                        myComputer.sendDamagePacket()
                        myComputer.watchHandler.updateInitialHealthQuantity(number, _health)
                        myComputerHandler.addData(
                            structuredMessage(
                                arrayOf(MessageHandler.HEAL_SUCCESS),
                                arrayOf(number, NumberFormat.getCurrencyInstance().format(cost))
                            ),
                            ip
                        )
                    }
                } else {
                    myComputerHandler.addData(
                        structuredMessage(arrayOf(MessageHandler.HEAL_FAIL_WEAKENED), arrayOf(number)),
                        MyApplicationData.getSourceIP()
                    )
                }
                return
            }

            if (command == EMPTY_PETTY_CASH_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                val nf = NumberFormat.getCurrencyInstance()
                val windowHandle = MyApplicationData.payloadAs<EmptyPettyCashPayload>().windowHandle
                if (type != BANKING) {
                    myComputerHandler.addData(
                        structuredMessage(
                            arrayOf(MessageHandler.EMPTY_PETTY_FAIL_WRONG_TYPE),
                            arrayOf<Any?>(),
                            arrayOf(windowHandle, ip)
                        ),
                        MyApplicationData.getSourceIP()
                    )
                } else if (!fireWall!!.getPettyCashFail(MyApplicationData.getSourceIP())) {
                    if (finalizeAllowed(MyApplicationData)) {
                        val amount = myComputer.getPettyCash() * fireWall!!.getPettyCashReduction(MyApplicationData.getSourceIP())
                        if (myComputerHandler.getMyComputerHandler().getComputer(MyApplicationData.getSourceIP())?.checkBank() == true) {
                            myComputerHandler.addData(ApplicationData(PettyCashDeltaPayload(-1.0f * amount), 0, ip), ip)
                            myComputerHandler.addData(ApplicationData(PettyCashDeltaPayload(amount), 0, MyApplicationData.getSourceIP()), MyApplicationData.getSourceIP())
                            myComputerHandler.addData(
                                structuredMessage(arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS_GAME), arrayOf(nf.format(amount), ip)),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(
                                structuredMessage(
                                    arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS),
                                    arrayOf(nf.format(amount)),
                                    arrayOf(windowHandle, MyApplicationData.getSourceIP())
                                ),
                                MyApplicationData.getSourceIP()
                            )
                        } else {
                            myComputerHandler.addData(
                                structuredMessage(arrayOf(MessageHandler.EMPTY_PETTY_FAIL_NO_ACTIVE_BANK), arrayOf(ip)),
                                MyApplicationData.getSourceIP()
                            )
                        }
                    }
                } else {
                    myComputerHandler.addData(structuredMessage(arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS), arrayOf(nf.format(0))), MyApplicationData.getSourceIP())
                    myComputerHandler.addData(
                        structuredMessage(
                            arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS_GAME),
                            arrayOf(nf.format(0), ip),
                            arrayOf(windowHandle, MyApplicationData.getSourceIP())
                        ),
                        MyApplicationData.getSourceIP()
                    )
                }
                resetPort(true, true)
                return
            } else if (command == FINALIZE_CANCELLED_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    resetPort(true, true)
                    return
                }
            }

            if (command == MALGET_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (type != FTP) {
                    myComputerHandler.addData(
                        structuredMessage(
                            arrayOf(MessageHandler.STEAL_FILE_FAIL_WRONG_TYPE),
                            arrayOf<Any?>(),
                            arrayOf(lastDamageWindowHandle, ip)
                        ),
                        MyApplicationData.getSourceIP()
                    )
                } else if (!fireWall!!.getStealFileFail(MyApplicationData.getSourceIP())) {
                    if (finalizeAllowed(MyApplicationData)) {
                        program!!.execute(MyApplicationData)
                    }
                }
                resetPort(true, true)
                return
            }

            if (command == DELETE_LOG_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    val payload = MyApplicationData.payloadAs<DeleteLogPayload>()
                    myComputer.deleteLogs(payload.ipAddress)
                    myComputerHandler.addData(textMessage(MessageHandler.DELETE_LOGS_SUCCESS), MyApplicationData.getSourceIP())
                    myComputer.sendPacket()
                }
                resetPort(true, true)
                return
            }

            if (command == PEEK_CODE_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        if (myComputer.getType() != Computer.NPC) {
                            myComputerHandler.addData(codeMessage(program!!.getContent() as HashMap<Any?, Any?>), MyApplicationData.getSourceIP())
                        } else {
                            myComputerHandler.addData(codeTextMessage("[Encrypted Data.]"), MyApplicationData.getSourceIP())
                        }
                    }
                }
                resetPort(true, true)
                return
            }

            if (command == PEEK_LOGS_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        val Logs = HashMap<Any?, Any?>()
                        Logs["logs"] = myComputer.logs
                        myComputerHandler.addData(codeMessage(Logs), MyApplicationData.getSourceIP())
                    }
                }
                resetPort(true, true)
                return
            }

            if (command == EDIT_LOGS_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        val payload = MyApplicationData.payloadAs<EditLogsPayload>()
                        val data = payload.data
                        val replace = payload.replace
                        myComputer.editLogs(data, replace)
                        myComputerHandler.addData(textMessage(MessageHandler.EDIT_LOGS_SUCCESS), MyApplicationData.getSourceIP())
                    }
                }
                resetPort(true, true)
                return
            }

            if (command == CHANGE_DAILY_PAY_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                val payload = MyApplicationData.payloadAs<ChangeDailyPayPayload>()
                val targetIP = payload.targetIp
                val port = payload.windowHandle
                if (!fireWall!!.getChangeDailyPayFail(targetIP)) {
                    myComputer.setDailyPayReduction(fireWall!!.getChangeDailyPayReduction(MyApplicationData.getSourceIP()))
                    if (type != HTTP) {
                        myComputerHandler.addData(
                            structuredMessage(
                                arrayOf(MessageHandler.CHANGE_DAILY_PAY_FAIL_WRONG_TYPE),
                                arrayOf<Any?>(),
                                arrayOf(lastDamageWindowHandle, ip)
                            ),
                            MyApplicationData.getSourceIP()
                        )
                    } else if (finalizeAllowed(MyApplicationData)) {
                        if (myComputer.lastBountyHTTPIP == MyApplicationData.getSourceIP()) {
                            myComputerHandler.addData(textMessage(MessageHandler.CHANGE_DAILY_PAY_FAIL_BOUNTY), MyApplicationData.getSourceIP())
                            return
                        } else if (myComputer.getAdRevenueTarget() != targetIP) {
                            if (myComputer.getType() == Computer.NPC) {
                                myComputerHandler.addData(floatCommand("httpxp", 10.0f, MyApplicationData.getSourcePort()), MyApplicationData.getSourceIP())
                            } else {
                                myComputerHandler.addData(floatCommand("httpxp", 10.0f + myComputer.hTTPLevel * 10.0f, MyApplicationData.getSourcePort()), MyApplicationData.getSourceIP())
                            }

                            if (myComputer.getType() != Computer.NPC) {
                                myComputerHandler.addData(ApplicationData(DailyPaySetPayload(targetIP), 0, ip), MyApplicationData.getSourceIP())
                            }
                            myComputerHandler.addData(
                                structuredMessage(
                                    arrayOf(MessageHandler.CHANGE_DAILY_PAY_SUCCESS),
                                    arrayOf<Any?>(),
                                    arrayOf(port, ip)
                                ),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(textMessage(MessageHandler.CHANGE_DAILY_PAY_SUCCESS_GAME), MyApplicationData.getSourceIP())
                        } else {
                            myComputerHandler.addData(
                                structuredMessage(
                                    arrayOf(MessageHandler.CHANGE_DAILY_PAY_FAIL_ALREADY_CONTROLLED),
                                    arrayOf<Any?>(),
                                    arrayOf(port, ip)
                                ),
                                MyApplicationData.getSourceIP()
                            )
                        }
                        myComputer.setAdRevenueTarget(targetIP)
                    }
                } else {
                    myComputerHandler.addData(
                        structuredMessage(
                            arrayOf(MessageHandler.CHANGE_DAILY_PAY_SUCCESS),
                            arrayOf<Any?>(),
                            arrayOf(port, ip)
                        ),
                        MyApplicationData.getSourceIP()
                    )
                    myComputerHandler.addData(textMessage(MessageHandler.CHANGE_DAILY_PAY_SUCCESS_GAME), MyApplicationData.getSourceIP())
                }
                resetPort(true, true)
                return
            }

            if (command == DESTROY_WATCH_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                if (!myComputer.equipmentSheet.getDestroyWatchesImmune()) {
                    if (myComputer.getType() != Computer.NPC && finalizeAllowed(MyApplicationData)) {
                        myComputer.destroyWatches(number)
                        myComputerHandler.addData(textMessage(MessageHandler.DESTROY_WATCHES_SUCCESS), MyApplicationData.getSourceIP())
                    }
                    resetPort(true, true)
                }
                return
            }

            if (command == ATTACK_COMMAND || command == MINE_COMMAND) {
                if (command == MINE_COMMAND && type != REDIRECT) {
                    myComputerHandler.addData(textMessage(MessageHandler.REDIRECT_FAIL_WRONG_TYPE), MyApplicationData.getSourceIP())
                    return
                }
                val entryPayload = MyApplicationData.payloadAs<PortEntryPayload>()
                val network = entryPayload.network
                if (myComputer.getNetwork() != network) {
                    val network2 = myComputer.getNetwork()
                    myComputerHandler.addData(
                        structuredMessage(arrayOf(MessageHandler.ATTACK_FAIL_WRONG_NETWORK), arrayOf(ip, network2)),
                        MyApplicationData.getSourceIP()
                    )
                    return
                }
                if (myComputer.getTotalLevel() < myComputer.noobSafety) {
                    myComputerHandler.addData(textMessage(MessageHandler.ATTACK_FAIL_NOOB), MyApplicationData.getSourceIP())
                    return
                }
                if (_accessing.isEmpty() || ((MyApplicationData.getSourceIP() == _accessing && MyApplicationData.getSourcePort() == accessingPort) && !weakened)) {
                    _accessing = MyApplicationData.getSourceIP()
                    accessingPort = MyApplicationData.getSourcePort()

                    if (myComputer.getType() == Computer.NPC || _accessing != currentRedirectIP) {
                        currentRedirectXP = 0.0f
                        currentRedirectIP = _accessing
                    }

                    val attackInitialize = ApplicationData(
                        AttackInitializePayload(
                            0.0f,
                            _health,
                            myComputer.getPettyCash(),
                            getCPUCost(),
                            myComputer.watchHandler.checkForWatch(number),
                            myComputer.getType() == Computer.NPC
                        ),
                        MyApplicationData.getSourcePort(),
                        ip
                    ).withSourcePort(number)
                    if (entryPayload is LocalPortEntryPayload) {
                        myComputerHandler.addData(attackInitialize, MyApplicationData.getSourceIP())
                    } else {
                        myComputerHandler.addData(attackInitialize, (entryPayload as RedirectedPortEntryPayload).targetIp)
                    }
                    lastAccessed = now
                } else {
                    myComputerHandler.addData(
                        structuredMessage(
                            arrayOf(MessageHandler.PORT_ALREADY_UNDER_ATTACK),
                            arrayOf(number, ip, _accessing),
                            arrayOf(MyApplicationData.getSourcePort(), _accessing)
                        ),
                        MyApplicationData.getSourceIP()
                    )
                }
                return
            }

            if (command == CANCEL_ATTACK_COMMAND && accessing == MyApplicationData.getSourceIP()) {
                val payload = MyApplicationData.payloadAs<CancelAttackPayload>()
                resetPort(payload.heal ?: true, true)
                return
            }

            if (command == FREEZE_COMMAND) {
                if (!myComputer.equipmentSheet.getFreezeImmune()) {
                    if (accessing == MyApplicationData.getSourceIP()) {
                        freeze = true
                        freezeStart = myComputer.currentTime
                    }
                }
                return
            }

            if (command == DAMAGE_COMMAND && !weakened) {
                val initialHealth = _health
                val payload = MyApplicationData.payloadAs<DamagePayload>()
                val damage = payload.damage
                val damageFromFireWall = payload.damageFromFireWall
                val zombieDamage = payload.zombieSource != null
                val zombieSource = payload.zombieSource
                _lastDamageWindowHandle = payload.windowHandle
                _lastDamageIP = MyApplicationData.getSourceIP()
                val currentCommodity = payload.commodityId

                val modify =
                    if (!zombieDamage) {
                        fireWall!!.modifyDamage(damage, MyApplicationData.getSourceIP(), MyApplicationData.getSourcePort(), damageFromFireWall)
                    } else {
                        fireWall!!.modifyDamage(damage, zombieSource, MyApplicationData.getSourcePort(), damageFromFireWall)
                    }
                damagePort(modify)

                val xp = damage
                var mining = false
                if (currentCommodity != -1) {
                    mining = true
                    val currentAmount = myComputer.getCommodity(currentCommodity)
                    var sendAmount = 1.0f

                    if (health <= 0.0f && myComputer.getType() == Computer.NPC) {
                        sendAmount = currentAmount
                    }

                    if (initialHealth == 100.0f && myComputer.getType() == Computer.NPC && currentAmount <= 0.0f) {
                        myComputer.respawnCommodity(currentCommodity)
                    }

                    if (currentAmount > 0.0f) {
                        if ((100.0f - health) >= 100.0f / currentAmount) {
                            myComputer.setCommodityAmount(currentCommodity, currentAmount - sendAmount)
                            myComputerHandler.addData(
                                ApplicationData(CommodityPayload(currentCommodity, sendAmount, MyApplicationData.getSourcePort(), IP), MyApplicationData.getSourcePort(), ip),
                                MyApplicationData.getSourceIP()
                            )
                            currentRedirectXP += Computer.commodityXP[currentCommodity]
                            if (currentRedirectXP < MAX_REDIRECT_XP) {
                                myComputerHandler.addData(
                                    floatCommand("redirectxp", Computer.commodityXP[currentCommodity] * sendAmount, MyApplicationData.getSourcePort()),
                                    MyApplicationData.getSourceIP()
                                )
                            } else {
                                myComputerHandler.addData(
                                    structuredMessage(arrayOf(MessageHandler.REDIRECT_XP_MAX), arrayOf(ip)),
                                    MyApplicationData.getSourceIP()
                                )
                            }
                        }
                    }
                }

                if (xp > 0) {
                    val values = CombatDamageValues(
                        xp = xp,
                        health = _health,
                        pettyCash = myComputer.getPettyCash(),
                        cpuCost = getCPUCost(),
                        damage = modify
                    )
                    val resolutionPayload = when {
                        zombieDamage -> CombatOpponentUpdatePayload(
                            values = values,
                            targetWatch = myComputer.watchHandler.checkForWatch(number),
                            damageFromFirewall = damageFromFireWall,
                            mining = mining
                        )

                        !mining -> CombatAttackXpUpdatePayload(
                            values = values,
                            targetWatch = myComputer.watchHandler.checkForWatch(number),
                            damageFromFirewall = damageFromFireWall,
                            mining = false,
                            targetIp = null
                        )

                        else -> CombatMiningDamageUpdatePayload(
                            values = values,
                            targetWatch = myComputer.watchHandler.checkForWatch(number),
                            damageFromFirewall = damageFromFireWall,
                            mining = true,
                            targetIp = null
                        )
                    }
                    if (zombieDamage) {
                        myComputerHandler.addData(ApplicationData(resolutionPayload, MyApplicationData.getSourcePort(), ip), zombieSource)
                    }
                    myComputerHandler.addData(ApplicationData(resolutionPayload, MyApplicationData.getSourcePort(), ip), MyApplicationData.getSourceIP())
                }

                if (health <= 0.0f && accessing == MyApplicationData.getSourceIP()) {
                    val AD = ApplicationData(AttackFinalizePayload(type), MyApplicationData.getSourcePort(), ip)
                        .withSourcePort(number)
                    if (!zombieDamage) {
                        myComputerHandler.addData(AD, MyApplicationData.getSourceIP())
                    } else {
                        myComputerHandler.addData(AD, zombieSource)
                    }

                    _weakened = true
                    resetHealCounter()
                } else if (health <= 0.0f) {
                    _weakened = true
                    resetHealCounter()
                }

                lastAccessed = now
                myComputer.sendDamagePacket()
                return
            }

            if (command == installScriptCommand && accessing == MyApplicationData.getSourceIP()) {
                if (!fireWall!!.getInstallScriptFail(MyApplicationData.getSourceIP())) {
                    if (myComputer.getType() != Computer.NPC && finalizeAllowed(MyApplicationData)) {
                        val payload = MyApplicationData.payloadAs<InstallScriptPayload>()
                        val Script = payload.script as HashMap<Any?, Any?>?
                        val MaliciousParameters = payload.maliciousParameters as Array<Any?>

                        if (type == BANKING) {
                            maliciousTarget = MaliciousParameters[0] as String
                            val B = program as Banking
                            B.pettyCashTarget = MaliciousParameters[1] as Float
                        } else if (type == FTP) {
                            maliciousTarget = MaliciousParameters[0] as String
                        } else if (type == ATTACK) {
                            val A = program as AttackProgram
                            A.setPettyCashTarget(MaliciousParameters[1] as Float)
                            maliciousTarget = MaliciousParameters[0] as String
                        }

                        if (Script != null && program != null) {
                            program!!.installScript(Script)
                            myComputerHandler.addData(
                                structuredMessage(
                                    arrayOf(MessageHandler.INSTALL_SCRIPT_SUCCESS),
                                    arrayOf<Any?>(),
                                    arrayOf(lastDamageWindowHandle, ip)
                                ),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(
                                structuredMessage(arrayOf(MessageHandler.INSTALL_SCRIPT_SUCCESS_GAME), arrayOf<Any?>()),
                                MyApplicationData.getSourceIP()
                            )
                        }
                    }
                }
                resetPort(true, true)
                return
            }

            if (!dummy) {
                if (command == ATTACK_FINALIZE_COMMAND) {
                    program!!.execute(MyApplicationData)
                } else if ((!overHeated && !freeze) || (command == REQUEST_SECONDARY_DIRECTORY_COMMAND)) {
                    if (command != MALGET_COMMAND) {
                        program!!.execute(MyApplicationData)
                    }
                } else {
                    if (command == ZOMBIE_ATTACK_COMMAND) {
                        myComputerHandler.addData(
                            structuredMessage(arrayOf(MessageHandler.PORT_IS_OVERHEATED), arrayOf(number, ip)),
                            MyApplicationData.getSourceIP()
                        )
                    }
                    myComputerHandler.addData(
                        structuredMessage(arrayOf(MessageHandler.PORT_IS_OVERHEATED), arrayOf(number, ip)),
                        ip
                    )
                }
            } else {
                myComputerHandler.addData(
                    structuredMessage(
                        arrayOf(MessageHandler.PORT_WAS_DUMMY),
                        arrayOf(number, ip),
                        arrayOf(MyApplicationData.sourcePort, MyApplicationData.sourceIP)
                    ),
                    ip
                )
            }
        } else if (command != LOG_MESSAGE_COMMAND) {
            attacking = false
            myComputerHandler.addData(
                structuredMessage(arrayOf(MessageHandler.COULD_NOT_EXECUTE_APPLICATION), arrayOf(number)),
                ip
            )
        }
    }

    fun friendlyPut(MyApplicationData: ApplicationData) {
        if (MyApplicationData.command == FINALIZE_PUT_COMMAND) {
            val payload = MyApplicationData.payloadAs<FinalizePutPayload>()
            val fetchPath = payload.fetchPath
            val hf = payload.file
            myComputerHandler.addData(messageData(MessageHandler.FTP_PUT_FAIL, IP), MyApplicationData.getSourceIP())
            hf.setLocation("")
            myComputerHandler.addData(ApplicationData(SaveFile(MyApplicationData.getSourceIP(), fetchPath, hf), 0, IP), MyApplicationData.getSourceIP())
        }
    }

    private fun textMessage(message: Any): ApplicationData {
        return messageData(message, ip)
    }

    private fun structuredMessage(
        message: Array<out Any?>,
        parameters: Array<out Any?>? = null,
        portInfo: Array<out Any?>? = null
    ): ApplicationData {
        return ApplicationData(
            StructuredMessagePayload(
                arrayOf(*message),
                parameters?.let { arrayOf(*it) },
                portInfo?.let { arrayOf(*it) }
            ),
            0,
            ip
        )
    }

    private fun floatCommand(command: String, value: Float, sourcePort: Int): ApplicationData {
        return ApplicationData(FloatCommandPayload(ApplicationCommand.of(command), value), sourcePort, ip)
    }

    @Suppress("UNCHECKED_CAST")
    private fun codeMessage(data: HashMap<Any?, Any?>): ApplicationData {
        return ApplicationData(MapCommandPayload(codeCommand, data as HashMap<Any, Any>), 0, ip)
    }

    private fun codeTextMessage(text: String): ApplicationData {
        return ApplicationData(StringCommandPayload(codeCommand, text), 0, ip)
    }

    private fun saveFileMessage(targetIp: String, path: String, file: HackerFile): ApplicationData {
        return ApplicationData(SaveFile(targetIp, path, file), 0, ip)
    }

    private fun requestFtpUpdateMessage(): ApplicationData {
        return ApplicationData(RequestFtpUpdatePayload, 0, ip)
    }

    fun getPacketPort(): PacketPort {
        val returnMe = PacketPort()
        returnMe.setNote(note)
        returnMe.setNumber(number)
        returnMe.setFireWall(fireWall!!.getType())
        returnMe.setType(type)
        returnMe.setOn(on)
        returnMe.setAttacking(attacking)
        returnMe.setCPUCost(getCPUCost())
        returnMe.setMaxCPUCost(getBaseCPUCostAndFirewall())
        returnMe.setHealth(_health)
        returnMe.setDummy(dummy)
        returnMe.setDefault(0)
        if (getType() == PacketPort.BANKING && getNumber() == myComputer.getDefaultBank()) returnMe.setDefault(1)
        if (getType() == PacketPort.ATTACK && getNumber() == myComputer.getDefaultAttack()) returnMe.setDefault(1)
        if (getType() == PacketPort.REDIRECT && getNumber() == myComputer.getDefaultShipping()) returnMe.setDefault(1)
        if (getType() == PacketPort.FTP && getNumber() == myComputer.getDefaultFTP()) returnMe.setDefault(1)
        if (getType() == PacketPort.HTTP && getNumber() == myComputer.getDefaultHTTP()) returnMe.setDefault(1)
        return returnMe
    }

    fun outputXML(): String {
        var returnMe = "<port>\n"
        returnMe += "<number>$number</number>\n"
        returnMe += "<type>$type</type>\n"
        returnMe += "<health>$_health</health>"
        returnMe += if (on) "<onoff>1</onoff>\n" else "<onoff>0</onoff>\n"
        returnMe += "<cpu>$_cpuCost</cpu>\n"
        returnMe += if (note != null) {
            "<note><![CDATA[" + note!!.replace("]]>", "]]&gt;") + "]]></note>\n"
        } else {
            "<note><![CDATA[$note]]></note>\n"
        }
        returnMe += "<firewall>" + fireWall!!.getHackerFile()!!.outputXML() + "</firewall>\n"
        returnMe += if (dummy) "<dummy>1</dummy>\n" else "<dummy>0</dummy>\n"
        returnMe += if (maliciousTarget != null) {
            "<malicioustarget><![CDATA[" + maliciousTarget!!.replace("]]>", "]]&gt;") + "]]></malicioustarget>\n"
        } else {
            "<malicioustarget><![CDATA[$maliciousTarget]]></malicioustarget>\n"
        }
        if (program != null) {
            returnMe += program!!.outputXML()
        }
        returnMe += "</port>\n"
        return returnMe
    }

    companion object {
        const val BANKING = 0
        const val FTP = 1
        const val ATTACK = 2
        const val HTTP = 3
        const val REDIRECT = 4
        const val SHIPPING = REDIRECT
        const val MAX_REDIRECT_XP = 2000.0f
        private const val FREEZE_TIME = 10000L
        const val timeOut = 30000L
        const val fullTimeOut = 45000L
    }
}
