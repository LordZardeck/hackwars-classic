package com.hackwars.game.program

import game.*
import hackscript.model.RunFactory

/**
 * Class which contains the banking application.
 */

class Banking(MyComputerHandler: NetworkSwitch?, MyComputer: Computer?, ParentPort: Port?) : Program() {
    private var depositScript: String? = null //Script which runs when a deposit is requested.
    private var withdrawScript: String? = null //Script which runs when withdraw is requested.
    private var transferScript: String? = null //Script which runs when a transfer is requested.

    private var MyComputerHandler: NetworkSwitch? = null //Used to dispatch ApplicationData to other computers.
    private var MyComputer: Computer? = null //The computer executing this program.

    /* The amount which has been provided for this transaction.
       In the case of withdraws the script running can't exceed this amount.*/
    private var amount = 0.0f
    private var initialAmount = 0.0f

    /**
     * Set the petty cash amount used to decide when stealing should take place.
     */
    var pettyCashTarget: Float = 0.0f
        /**
         * Get the petty cash amount used to decide when stealing should take place.
         */
        get() = (field)
    private var withdraw = false

    private var ParentPort: Port? = null //NOT CURRENTLY USED. The port with this program installed.
    private var targetIP = "" //In the case of a transfer the IP that is being targeted.

    /**
     * Deposit money in the the players bank.
     */
    fun deposit(amount: Float) {
        MyComputer!!.setBank(MyComputer!!.getBank() + amount)
    }

    val iP: String?
        /**
         * Get the IP address of the computer that this program is installed on.
         */
        get() = (MyComputer!!.getIP())

    val maliciousTarget: String?
        /**
         * Get the malicious IP that should be delivered money.
         */
        get() = (ParentPort!!.getMaliciousTarget())

    /**
     * Return the target IP of a money transfer.
     */
    fun getTargetIP(): String? {
        return (targetIP)
    }

    /**
     * Return the amount that has been requested for the transaction.
     */
    fun getAmount(): Float {
        return (amount)
    }

    /**
     * Set the amount still available from the transaction request.
     */
    fun setAmount(amount: Float) {
        this.amount = amount
    }

    /**
     * Return the initial amount that has been requested for the transaction.
     */
    fun getInitialAmount(): Float {
        return (initialAmount)
    }

    /**
     * Get whether the requested operation was a withdraw.
     */
    fun isWithdraw(): Boolean {
        return (withdraw)
    }

    /**
     * Get whether the requested operation was a withdraw.
     */
    var deposit: Boolean = false

    fun isDeposit(): Boolean {
        return (deposit)
    }

    /*
    Get whether the requested operation was a withdraw.
    */
    var transfer: Boolean = false

    //constructor
    init {
        super.setComputerHandler(MyComputerHandler)
        super.setComputer(MyComputer)
        this.MyComputerHandler = MyComputerHandler
        this.MyComputer = MyComputer
        this.ParentPort = ParentPort
    }

    fun isTransfer(): Boolean {
        return (transfer)
    }


    var pettyCash: Float
        /**
         * Return the amount in the pettycash of the computer this is attached to.
         */
        get() = (MyComputer!!.getPettyCash())
        /**
         * Set the amount of money in the computer's petty cash.
         */
        set(pettyCash) {
            MyComputer!!.setPettyCash(pettyCash)
        }

    var bank: Float
        /**
         * Return the amount in the pettycash of the computer this is attached to.
         */
        get() = (MyComputer!!.getBank())
        /**
         * Set the amount of money in the computer's petty cash.
         */
        set(bankMoney) {
            MyComputer!!.setBank(bankMoney)
        }

    val bankMoney: Float
        /**
         * Return the amount in the bank of the computer this is attached to.
         */
        get() = (MyComputer!!.getBankMoney())

    /**
     * Set the script to run when a deposit is performed.
     */
    fun setDepositScript(depositScript: String?) {
        this.depositScript = depositScript
    }

    /**
     * Get the script that is to run when a deposit is performed.
     */
    fun getDepositScript(): String? {
        return (depositScript)
    }

    /**
     * Set the script that is to run when a withdraw is performed.
     */
    fun setWithdrawScript(withdrawScript: String?) {
        this.withdrawScript = withdrawScript
    }

    /**
     * Get the script that is to run when a withdraw is performed.
     */
    fun getWithdrawScript(): String? {
        return (withdrawScript)
    }

    /**
     * Set the script that is to run when a transfer is requested.
     */
    fun setTransferScript(transferScript: String?) {
        this.transferScript = transferScript
    }

    /**
     * Provides an ApplicationData packet and executes scripts accordingly.
     */
    override fun execute(MyApplicationData: ApplicationData) {
        var data: String? = null

        //A 'deposit' function call.
        if (MyApplicationData.getFunction() == "deposit") {
            amount = (MyApplicationData.getParameters() as kotlin.Float?)!!
            initialAmount = amount
            if (amount > MyComputer!!.getPettyCash()) amount = MyComputer!!.getPettyCash()
            data = depositScript
            withdraw = false
            deposit = true
            transfer = false
        } else  //A 'withdraw' function call.
            if (MyApplicationData.getFunction() == "withdraw") {
                amount = (MyApplicationData.getParameters() as kotlin.Float?)!!
                initialAmount = amount
                if (amount > MyComputer!!.getBankMoney()) {
                    amount = MyComputer!!.getBankMoney()
                }
                data = withdrawScript
                withdraw = true
                deposit = false
                transfer = false
            } else  //A 'transfer' function call.
                if (MyApplicationData.getFunction() == "transfer") {
                    val parameters = MyApplicationData.getParameters() as Array<Any?>
                    targetIP = parameters[0] as String

                    //We make an exception for Alexi.
                    if (MyComputer!!.getTotalLevel() < 15 && targetIP != "900.800.7.012") { //Noob check.
                        MyComputer!!.addMessage(MessageHandler.TRANSFER_FAIL_NOOB)
                        MyComputer!!.sendPacket()
                        return
                    }

                    amount = (parameters[1] as Float?)!!
                    initialAmount = amount
                    if (amount > MyComputer!!.getPettyCash()) amount = MyComputer!!.getPettyCash()
                    data = transferScript
                    withdraw = false
                    transfer = true
                    deposit = false
                } else return

        try {
            val HL = HackerLinker(this, MyComputerHandler)

            RunFactory.runCode(data, HL, MyComputer!!.MAX_OPS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * installScript(HashMap Script);
     * Installs a script on the various entrance points on this program.
     */
    override fun installScript(Script: HashMap<*, *>) {
        depositScript = Script.get("deposit") as String?
        withdrawScript = Script.get("withdraw") as String?
        transferScript = Script.get("transfer") as String?
    }

    /**
     * Returns the keys associated with this program type.
     */
    override fun getTypeKeys(): Array<String?> {
        val returnMe: Array<String?>? = arrayOf<String?>("deposit", "withdraw", "transfer")
        return (returnMe!!)
    }

    /**
     * Return a hash map representation of the program currently installed on this port.
     */
    override fun getContent(): HashMap<*, *> {
        val returnMe: HashMap<Any?, Any?> = HashMap()
        returnMe.put("deposit", depositScript)
        returnMe.put("withdraw", withdrawScript)
        returnMe.put("transfer", transferScript)
        return (returnMe)
    }


    /**
     * Output the class data in XML format.
     */
    override fun outputXML(): String {
        var returnMe: String? = "<code>\n"
        returnMe += "<withdraw><![CDATA["
        if (withdrawScript != null) returnMe += withdrawScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += withdrawScript
        returnMe += "]]></withdraw>\n"
        returnMe += "<deposit><![CDATA["
        if (depositScript != null) returnMe += depositScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += depositScript
        returnMe += "]]></deposit>\n"
        returnMe += "<transfer><![CDATA["
        if (transferScript != null) returnMe += transferScript!!.replace("]]>".toRegex(), "]]&gt;")
        else returnMe += transferScript
        returnMe += "]]></transfer>\n"
        returnMe += "</code>\n"
        return (returnMe)
    }
}
