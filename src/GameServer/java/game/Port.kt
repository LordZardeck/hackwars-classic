package game

import assignments.PacketAssignment
import assignments.PacketPort
import com.hackwars.game.program.AttackProgram
import com.hackwars.game.program.Banking
import com.hackwars.game.program.Program
import com.hackwars.game.program.ShippingProgram
import java.text.NumberFormat
import kotlin.jvm.JvmName

class Port(
    @get:JvmName("getMyComputerProperty")
    val myComputer: Computer,
    private val myComputerHandler: NetworkSwitch
) {
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
                    ApplicationData(
                        "message",
                        arrayOf(
                            MessageHandler.PORT_WAS_DUMMY,
                            arrayOf(number, ip),
                            arrayOf(MyApplicationData.getSourcePort(), MyApplicationData.getSourceIP())
                        ),
                        0,
                        ip
                    ),
                    MyApplicationData.getSourceIP()
                )
            }
        } else {
            allowed = false
            myComputerHandler.addData(
                ApplicationData(
                    "message",
                    arrayOf(
                        MessageHandler.PORT_WAS_NOT_WEAKENED,
                        arrayOf(number, ip),
                        arrayOf(MyApplicationData.getSourcePort(), MyApplicationData.getSourceIP())
                    ),
                    0,
                    ip
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
        if (on) {
            if (MyApplicationData.getFunction() == "heal") {
                if (!myComputer.checkBank()) {
                    myComputerHandler.addData(
                        ApplicationData("message", MessageHandler.ACTIVE_BANK_NOT_FOUND, 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                } else if (overHeated) {
                    myComputerHandler.addData(
                        ApplicationData("message", MessageHandler.HEAL_FAIL_OVERHEATED, 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                } else if (healCount > myComputer.HEAL_LIMIT) {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.HEAL_FAIL_LIMIT, arrayOf(myComputer.HEAL_LIMIT + 1)), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                } else if (!weakened) {
                    val cost = (maximumHealth - health) * 2.0f * myComputer.equipmentSheet.getHealBonus()
                    if (myComputer.getPettyCash() >= cost) {
                        healCount++
                        myComputerHandler.addData(ApplicationData("pettycash", java.lang.Float(cost * -1.0f), 0, ip), ip)
                        health = maximumHealth
                        myComputer.sendDamagePacket()
                        myComputer.watchHandler.updateInitialHealthQuanity(number, _health)
                        myComputerHandler.addData(
                            ApplicationData("message", arrayOf(MessageHandler.HEAL_SUCCESS, arrayOf(number, NumberFormat.getCurrencyInstance().format(cost))), 0, ip),
                            ip
                        )
                    }
                } else {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.HEAL_FAIL_WEAKENED, arrayOf(number)), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                }
                return
            }

            if (MyApplicationData.getFunction() == "emptyPettyCash" && accessing == MyApplicationData.getSourceIP()) {
                val nf = NumberFormat.getCurrencyInstance()
                val windowHandle = MyApplicationData.getParameters() as Int
                if (type != BANKING) {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_FAIL_WRONG_TYPE, arrayOf<Any?>(), arrayOf(windowHandle, ip)), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                } else if (!fireWall!!.getPettyCashFail(MyApplicationData.getSourceIP())) {
                    if (finalizeAllowed(MyApplicationData)) {
                        val amount = myComputer.getPettyCash() * fireWall!!.getPettyCashReduction(MyApplicationData.getSourceIP())
                        if (myComputerHandler.getMyComputerHandler().getComputer(MyApplicationData.getSourceIP()).checkBank()) {
                            myComputerHandler.addData(ApplicationData("pettycash", java.lang.Float(-1.0 * amount), 0, ip), ip)
                            myComputerHandler.addData(ApplicationData("pettycash", java.lang.Float(amount), 0, MyApplicationData.getSourceIP()), MyApplicationData.getSourceIP())
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS_GAME, arrayOf(nf.format(amount), ip)), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS, arrayOf(nf.format(amount)), arrayOf(windowHandle, MyApplicationData.getSourceIP())), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                        } else {
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_FAIL_NO_ACTIVE_BANK, arrayOf(ip)), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                        }
                    }
                } else {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS, arrayOf(nf.format(0))), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.EMPTY_PETTY_SUCCESS_GAME, arrayOf(nf.format(0), ip), arrayOf(windowHandle, MyApplicationData.getSourceIP())), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                }
                resetPort(true, true)
                return
            } else if (MyApplicationData.getFunction() == "finalizecancelled" && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    resetPort(true, true)
                    return
                }
            }

            if (MyApplicationData.getFunction() == "malget" && accessing == MyApplicationData.getSourceIP()) {
                if (type != FTP) {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.STEAL_FILE_FAIL_WRONG_TYPE, arrayOf<Any?>(), arrayOf(lastDamageWindowHandle, ip)), 0, ip),
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

            if (MyApplicationData.getFunction() == "deletelog" && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (MyApplicationData.getParameters() != null) {
                        myComputer.deleteLogs(MyApplicationData.getParameters() as String)
                        myComputerHandler.addData(ApplicationData("message", MessageHandler.DELETE_LOGS_SUCCESS, 0, ip), MyApplicationData.getSourceIP())
                        myComputer.sendPacket()
                    }
                }
                resetPort(true, true)
                return
            }

            if (MyApplicationData.getFunction() == "peekcode" && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        if (myComputer.getType() != Computer.NPC) {
                            myComputerHandler.addData(ApplicationData("code", program!!.getContent(), 0, ip), MyApplicationData.getSourceIP())
                        } else {
                            myComputerHandler.addData(ApplicationData("code", "[Encrypted Data.]", 0, ip), MyApplicationData.getSourceIP())
                        }
                    }
                }
                resetPort(true, true)
                return
            }

            if (MyApplicationData.getFunction() == "peeklogs" && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        val Logs = HashMap<Any?, Any?>()
                        Logs["logs"] = myComputer.logs
                        myComputerHandler.addData(ApplicationData("code", Logs, 0, ip), MyApplicationData.getSourceIP())
                    }
                }
                resetPort(true, true)
                return
            }

            if (MyApplicationData.getFunction() == "editLogs" && accessing == MyApplicationData.getSourceIP()) {
                if (finalizeAllowed(MyApplicationData)) {
                    if (program != null) {
                        val data = (MyApplicationData.getParameters() as Array<Any?>)[0] as String
                        val replace = (MyApplicationData.getParameters() as Array<Any?>)[1] as String
                        myComputer.editLogs(data, replace)
                        myComputerHandler.addData(ApplicationData("message", MessageHandler.EDIT_LOGS_SUCCESS, 0, ip), MyApplicationData.getSourceIP())
                    }
                }
                resetPort(true, true)
                return
            }

            if (MyApplicationData.getFunction() == "changedailypay" && accessing == MyApplicationData.getSourceIP()) {
                val parameters = MyApplicationData.getParameters() as Array<Any?>
                val targetIP = parameters[0] as String
                val port = parameters[1] as Int
                if (!fireWall!!.getChangeDailyPayFail(targetIP)) {
                    myComputer.setDailyPayReduction(fireWall!!.getChangeDailyPayReduction(MyApplicationData.getSourceIP()))
                    if (type != HTTP) {
                        myComputerHandler.addData(
                            ApplicationData("message", arrayOf(MessageHandler.CHANGE_DAILY_PAY_FAIL_WRONG_TYPE, arrayOf<Any?>(), arrayOf(lastDamageWindowHandle, ip)), 0, ip),
                            MyApplicationData.getSourceIP()
                        )
                    } else if (finalizeAllowed(MyApplicationData)) {
                        if (myComputer.lastBountyHTTPIP == MyApplicationData.getSourceIP()) {
                            myComputerHandler.addData(ApplicationData("message", MessageHandler.CHANGE_DAILY_PAY_FAIL_BOUNTY, 0, ip), MyApplicationData.getSourceIP())
                            return
                        } else if (myComputer.getAdRevenueTarget() != targetIP) {
                            if (myComputer.getType() == Computer.NPC) {
                                myComputerHandler.addData(ApplicationData("httpxp", java.lang.Float(10.0f), MyApplicationData.getSourcePort(), ip), MyApplicationData.getSourceIP())
                            } else {
                                myComputerHandler.addData(
                                    ApplicationData("httpxp", java.lang.Float(10.0f + myComputer.hTTPLevel * 10.0f), MyApplicationData.getSourcePort(), ip),
                                    MyApplicationData.getSourceIP()
                                )
                            }

                            if (myComputer.getType() != Computer.NPC) {
                                myComputerHandler.addData(ApplicationData("dailypayset", targetIP, 0, ip), MyApplicationData.getSourceIP())
                            }
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.CHANGE_DAILY_PAY_SUCCESS, arrayOf<Any?>(), arrayOf(port, ip)), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(ApplicationData("message", MessageHandler.CHANGE_DAILY_PAY_SUCCESS_GAME, 0, ip), MyApplicationData.getSourceIP())
                        } else {
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.CHANGE_DAILY_PAY_FAIL_ALREADY_CONTROLLED, arrayOf<Any?>(), arrayOf(port, ip)), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                        }
                        myComputer.setAdRevenueTarget(targetIP)
                    }
                } else {
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.CHANGE_DAILY_PAY_SUCCESS, arrayOf<Any?>(), arrayOf(port, ip)), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                    myComputerHandler.addData(ApplicationData("message", MessageHandler.CHANGE_DAILY_PAY_SUCCESS_GAME, 0, ip), MyApplicationData.getSourceIP())
                }
                resetPort(true, true)
                return
            }

            if (MyApplicationData.getFunction() == "destroyWatch" && accessing == MyApplicationData.getSourceIP()) {
                if (!myComputer.equipmentSheet.getDestroyWatchesImmune()) {
                    if (myComputer.getType() != Computer.NPC && finalizeAllowed(MyApplicationData)) {
                        myComputer.destroyWatches(number)
                        myComputerHandler.addData(ApplicationData("message", MessageHandler.DESTROY_WATCHES_SUCCESS, 0, ip), MyApplicationData.getSourceIP())
                    }
                    resetPort(true, true)
                }
                return
            }

            if (MyApplicationData.getFunction() == "attack" || MyApplicationData.getFunction() == "mine") {
                if (MyApplicationData.getFunction() == "mine" && type != REDIRECT) {
                    myComputerHandler.addData(ApplicationData("message", MessageHandler.REDIRECT_FAIL_WRONG_TYPE, 0, ip), MyApplicationData.getSourceIP())
                    return
                }
                var network = ""
                if (MyApplicationData.getParameters() is String) {
                    network = MyApplicationData.getParameters() as String
                }
                if (MyApplicationData.getParameters() is Array<*>) {
                    network = (MyApplicationData.getParameters() as Array<String>)[1]
                }
                if (myComputer.getNetwork() != network) {
                    val network2 = myComputer.getNetwork()
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.ATTACK_FAIL_WRONG_NETWORK, arrayOf(ip, network2)), 0, ip),
                        MyApplicationData.getSourceIP()
                    )
                    return
                }
                if (myComputer.getTotalLevel() < myComputer.noobSafety) {
                    myComputerHandler.addData(ApplicationData("message", MessageHandler.ATTACK_FAIL_NOOB, 0, ip), MyApplicationData.getSourceIP())
                    return
                }
                if (_accessing.isEmpty() || ((MyApplicationData.getSourceIP() == _accessing && MyApplicationData.getSourcePort() == accessingPort) && !weakened)) {
                    _accessing = MyApplicationData.getSourceIP()
                    accessingPort = MyApplicationData.getSourcePort()

                    if (myComputer.getType() == Computer.NPC || _accessing != currentRedirectIP) {
                        currentRedirectXP = 0.0f
                        currentRedirectIP = _accessing
                    }

                    val F = arrayOf(java.lang.Float(0.0f), java.lang.Float(_health), java.lang.Float(myComputer.getPettyCash()), java.lang.Float(getCPUCost()))
                    val O = arrayOf<Any?>(F, java.lang.Boolean(myComputer.watchHandler.checkForWatch(number)), java.lang.Boolean(myComputer.getType() == Computer.NPC))
                    val AD = ApplicationData("attackinitialize", O, MyApplicationData.getSourcePort(), ip)
                    AD.sourcePort = number
                    if (MyApplicationData.getParameters() is String) {
                        myComputerHandler.addData(AD, MyApplicationData.getSourceIP())
                    } else {
                        myComputerHandler.addData(AD, (MyApplicationData.getParameters() as Array<String>)[0])
                    }
                    lastAccessed = now
                } else {
                    myComputerHandler.addData(
                        ApplicationData(
                            "message",
                            arrayOf(MessageHandler.PORT_ALREADY_UNDER_ATTACK, arrayOf(number, ip, _accessing), arrayOf(MyApplicationData.getSourcePort(), _accessing)),
                            0,
                            ip
                        ),
                        MyApplicationData.getSourceIP()
                    )
                }
                return
            }

            if (MyApplicationData.getFunction() == "cancelattack" && accessing == MyApplicationData.getSourceIP()) {
                var heal = true
                val parameters = MyApplicationData.getParameters()
                if (parameters != null) {
                    heal = parameters as Boolean
                }
                resetPort(heal, true)
                return
            }

            if (MyApplicationData.getFunction() == "freeze") {
                if (!myComputer.equipmentSheet.getFreezeImmune()) {
                    if (accessing == MyApplicationData.getSourceIP()) {
                        freeze = true
                        freezeStart = myComputer.currentTime
                    }
                }
                return
            }

            if (MyApplicationData.getFunction() == "damage" && !weakened) {
                val initialHealth = _health
                val parameters = MyApplicationData.getParameters() as Array<Any?>
                val damage = parameters[0] as Float
                val damageFromFireWall = parameters[3] as Boolean
                var zombieDamage = false
                val zombieSource = parameters[4] as String?
                _lastDamageWindowHandle = parameters[5] as Int
                _lastDamageIP = MyApplicationData.getSourceIP()
                val currentCommodity = parameters[6] as Int
                if (zombieSource != null) {
                    zombieDamage = true
                }

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
                                ApplicationData(
                                    "commodity",
                                    arrayOf(Integer(currentCommodity), java.lang.Float(sendAmount), MyApplicationData.getSourcePort(), IP),
                                    MyApplicationData.getSourcePort(),
                                    ip
                                ),
                                MyApplicationData.getSourceIP()
                            )
                            currentRedirectXP += Computer.commodityXP[currentCommodity]
                            if (currentRedirectXP < MAX_REDIRECT_XP) {
                                myComputerHandler.addData(
                                    ApplicationData("redirectxp", java.lang.Float(Computer.commodityXP[currentCommodity] * sendAmount), MyApplicationData.getSourcePort(), ip),
                                    MyApplicationData.getSourceIP()
                                )
                            } else {
                                myComputerHandler.addData(
                                    ApplicationData("message", arrayOf(MessageHandler.REDIRECT_XP_MAX, arrayOf(ip)), 0, ip),
                                    MyApplicationData.getSourceIP()
                                )
                            }
                        }
                    }
                }

                if (xp > 0) {
                    val F = arrayOf(java.lang.Float(xp), java.lang.Float(_health), java.lang.Float(myComputer.getPettyCash()), java.lang.Float(getCPUCost()), java.lang.Float(modify))
                    val O =
                        if (!zombieDamage) {
                            arrayOf<Any?>(F, java.lang.Boolean(myComputer.watchHandler.checkForWatch(number)), damageFromFireWall, mining)
                        } else {
                            arrayOf<Any?>(F, java.lang.Boolean(myComputer.watchHandler.checkForWatch(number)), zombieSource, damageFromFireWall, mining)
                        }
                    if (zombieDamage) {
                        myComputerHandler.addData(ApplicationData("opponentupdate", O, MyApplicationData.getSourcePort(), ip), zombieSource)
                    }
                    if (!mining) {
                        myComputerHandler.addData(ApplicationData("attackxp", O, MyApplicationData.getSourcePort(), ip), MyApplicationData.getSourceIP())
                    } else {
                        myComputerHandler.addData(ApplicationData("miningdamageupdate", O, MyApplicationData.getSourcePort(), ip), MyApplicationData.getSourceIP())
                    }
                }

                if (health <= 0.0f && accessing == MyApplicationData.getSourceIP()) {
                    val AD = ApplicationData("attackfinalize", Integer(type), MyApplicationData.getSourcePort(), ip)
                    AD.sourcePort = number
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

            if (MyApplicationData.getFunction() == "installScript" && accessing == MyApplicationData.getSourceIP()) {
                if (!fireWall!!.getInstallScriptFail(MyApplicationData.getSourceIP())) {
                    if (myComputer.getType() != Computer.NPC && finalizeAllowed(MyApplicationData)) {
                        val Script = (MyApplicationData.getParameters() as Array<Any?>)[0] as HashMap<Any?, Any?>?
                        val MaliciousParameters = (MyApplicationData.getParameters() as Array<Any?>)[1] as Array<Any?>

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
                                ApplicationData("message", arrayOf(MessageHandler.INSTALL_SCRIPT_SUCCESS, arrayOf<Any?>(), arrayOf(lastDamageWindowHandle, ip)), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                            myComputerHandler.addData(
                                ApplicationData("message", arrayOf(MessageHandler.INSTALL_SCRIPT_SUCCESS_GAME, arrayOf<Any?>()), 0, ip),
                                MyApplicationData.getSourceIP()
                            )
                        }
                    }
                }
                resetPort(true, true)
                return
            }

            if (!dummy) {
                if (MyApplicationData.getFunction() == "attackfinalize") {
                    program!!.execute(MyApplicationData)
                } else if ((!overHeated && !freeze) || (MyApplicationData.getFunction() == "requestsecondarydirectory")) {
                    if (MyApplicationData.getFunction() != "malget") {
                        program!!.execute(MyApplicationData)
                    }
                } else {
                    if (MyApplicationData.getFunction() == "zombieattack") {
                        myComputerHandler.addData(
                            ApplicationData("message", arrayOf(MessageHandler.PORT_IS_OVERHEATED, arrayOf(number, ip)), 0, ip),
                            MyApplicationData.getSourceIP()
                        )
                    }
                    myComputerHandler.addData(
                        ApplicationData("message", arrayOf(MessageHandler.PORT_IS_OVERHEATED, arrayOf(number, ip)), 0, ip),
                        ip
                    )
                }
            } else {
                myComputerHandler.addData(
                    ApplicationData("message", arrayOf(MessageHandler.PORT_WAS_DUMMY, arrayOf(number, ip), arrayOf(MyApplicationData.getSourcePort(), MyApplicationData.getSourceIP())), 0, ip),
                    ip
                )
            }
        } else if (MyApplicationData.getFunction() != "logmessage") {
            attacking = false
            myComputerHandler.addData(
                ApplicationData("message", arrayOf(MessageHandler.COULD_NOT_EXECUTE_APPLICATION, arrayOf(number)), 0, ip),
                ip
            )
        }
    }

    fun friendlyPut(MyApplicationData: ApplicationData) {
        if (MyApplicationData.getFunction() == "finalizeput") {
            val fetch_path = (MyApplicationData.getParameters() as Array<Any?>)[2] as String
            val HF = (MyApplicationData.getParameters() as Array<Any?>)[5] as HackerFile
            myComputerHandler.addData(ApplicationData("message", MessageHandler.FTP_PUT_FAIL, 0, IP), MyApplicationData.getSourceIP())
            HF.setLocation("")
            val Parameters = arrayOf<Any?>(fetch_path, HF)
            myComputerHandler.addData(ApplicationData("savefile", Parameters, 0, IP), MyApplicationData.getSourceIP())
        }
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
