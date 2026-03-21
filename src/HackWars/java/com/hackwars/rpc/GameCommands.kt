package com.hackwars.rpc

import game.ApplicationCommand

class GameCommandSpec constructor(@JvmField val wireName: String) {
    @JvmField
    val command: ApplicationCommand = ApplicationCommand.of(wireName)

    override fun toString(): String = wireName
}

object GameCommands {
    @JvmField
    val CHANGEDAILYPAY = GameCommandSpec(GameCommandWires.CHANGEDAILYPAY)
    @JvmField
    val CHANGENETWORK = GameCommandSpec(GameCommandWires.CHANGENETWORK)
    @JvmField
    val CHANGEWATCHPORT = GameCommandSpec(GameCommandWires.CHANGEWATCHPORT)
    @JvmField
    val CHANGEWATCHTYPE = GameCommandSpec(GameCommandWires.CHANGEWATCHTYPE)
    @JvmField
    val CLUEDATA = GameCommandSpec(GameCommandWires.CLUEDATA)
    @JvmField
    val COMPILEFILE = GameCommandSpec(GameCommandWires.COMPILEFILE)
    @JvmField
    val CREATEFOLDER = GameCommandSpec(GameCommandWires.CREATEFOLDER)
    @JvmField
    val DECOMPILEFILE = GameCommandSpec(GameCommandWires.DECOMPILEFILE)
    @JvmField
    val DELETEFILE = GameCommandSpec(GameCommandWires.DELETEFILE)
    @JvmField
    val DELETEFIREWALL = GameCommandSpec(GameCommandWires.DELETEFIREWALL)
    @JvmField
    val DELETEFOLDER = GameCommandSpec(GameCommandWires.DELETEFOLDER)
    @JvmField
    val DELETELOGS = GameCommandSpec(GameCommandWires.DELETELOGS)
    @JvmField
    val DELETEMULTI = GameCommandSpec(GameCommandWires.DELETEMULTI)
    @JvmField
    val DELETEWATCH = GameCommandSpec(GameCommandWires.DELETEWATCH)
    @JvmField
    val DEPOSIT = GameCommandSpec(GameCommandWires.DEPOSIT)
    @JvmField
    val DOCHALLENGE = GameCommandSpec(GameCommandWires.DOCHALLENGE)
    @JvmField
    val EMPTYPETTYCASH = GameCommandSpec(GameCommandWires.EMPTYPETTYCASH)
    @JvmField
    val EXIT = GameCommandSpec(GameCommandWires.EXIT)
    @JvmField
    val FACEBOOKDEPOSIT = GameCommandSpec(GameCommandWires.FACEBOOKDEPOSIT)
    @JvmField
    val FACEBOOKTRANSFER = GameCommandSpec(GameCommandWires.FACEBOOKTRANSFER)
    @JvmField
    val FACEBOOKUPDATE = GameCommandSpec(GameCommandWires.FACEBOOKUPDATE)
    @JvmField
    val FACEBOOKWITHDRAW = GameCommandSpec(GameCommandWires.FACEBOOKWITHDRAW)
    @JvmField
    val FETCHPORTS = GameCommandSpec(GameCommandWires.FETCHPORTS)
    @JvmField
    val FETCHWATCHES = GameCommandSpec(GameCommandWires.FETCHWATCHES)
    @JvmField
    val FINALIZECANCELLED = GameCommandSpec(GameCommandWires.FINALIZECANCELLED)
    @JvmField
    val GET = GameCommandSpec(GameCommandWires.GET)
    @JvmField
    val HACKTENDO_ACTIVATE = GameCommandSpec(GameCommandWires.HACKTENDO_ACTIVATE)
    @JvmField
    val HACKTENDO_TARGET = GameCommandSpec(GameCommandWires.HACKTENDO_TARGET)
    @JvmField
    val HEALPORT = GameCommandSpec(GameCommandWires.HEALPORT)
    @JvmField
    val INSTALLAPPLICATION = GameCommandSpec(GameCommandWires.INSTALLAPPLICATION)
    @JvmField
    val INSTALLEQUIPMENT = GameCommandSpec(GameCommandWires.INSTALLEQUIPMENT)
    @JvmField
    val INSTALLFIREWALL = GameCommandSpec(GameCommandWires.INSTALLFIREWALL)
    @JvmField
    val INSTALLWATCH = GameCommandSpec(GameCommandWires.INSTALLWATCH)
    @JvmField
    val MAKEBOUNTY = GameCommandSpec(GameCommandWires.MAKEBOUNTY)
    @JvmField
    val MALGET = GameCommandSpec(GameCommandWires.MALGET)
    @JvmField
    val PEEKCODE = GameCommandSpec(GameCommandWires.PEEKCODE)
    @JvmField
    val PEEKLOGS = GameCommandSpec(GameCommandWires.PEEKLOGS)
    @JvmField
    val PORTONOFF = GameCommandSpec(GameCommandWires.PORTONOFF)
    @JvmField
    val PUT = GameCommandSpec(GameCommandWires.PUT)
    @JvmField
    val REPAIREQUIPMENT = GameCommandSpec(GameCommandWires.REPAIREQUIPMENT)
    @JvmField
    val REPLACEAPPLICATION = GameCommandSpec(GameCommandWires.REPLACEAPPLICATION)
    @JvmField
    val REQUESTATTACK = GameCommandSpec(GameCommandWires.REQUESTATTACK)
    @JvmField
    val REQUESTCANCELATTACK = GameCommandSpec(GameCommandWires.REQUESTCANCELATTACK)
    @JvmField
    val REQUESTDIRECTORY = GameCommandSpec(GameCommandWires.REQUESTDIRECTORY)
    @JvmField
    val REQUESTEQUIPMENT = GameCommandSpec(GameCommandWires.REQUESTEQUIPMENT)
    @JvmField
    val REQUESTFILE = GameCommandSpec(GameCommandWires.REQUESTFILE)
    @JvmField
    val REQUESTGAME = GameCommandSpec(GameCommandWires.REQUESTGAME)
    @JvmField
    val REQUESTPAGE = GameCommandSpec(GameCommandWires.REQUESTPAGE)
    @JvmField
    val REQUESTPURCHASE = GameCommandSpec(GameCommandWires.REQUESTPURCHASE)
    @JvmField
    val REQUESTSAVE = GameCommandSpec(GameCommandWires.REQUESTSAVE)
    @JvmField
    val REQUESTSCAN = GameCommandSpec(GameCommandWires.REQUESTSCAN)
    @JvmField
    val REQUESTSECONDARYDIRECTORY = GameCommandSpec(GameCommandWires.REQUESTSECONDARYDIRECTORY)
    @JvmField
    val REQUESTTASK = GameCommandSpec(GameCommandWires.REQUESTTASK)
    @JvmField
    val REQUESTTRIGGER = GameCommandSpec(GameCommandWires.REQUESTTRIGGER)
    @JvmField
    val REQUESTWEBPAGE = GameCommandSpec(GameCommandWires.REQUESTWEBPAGE)
    @JvmField
    val REQUESTZOMBIEATTACK = GameCommandSpec(GameCommandWires.REQUESTZOMBIEATTACK)
    @JvmField
    val REQUESTZOMBIECANCELATTACK = GameCommandSpec(GameCommandWires.REQUESTZOMBIECANCELATTACK)
    @JvmField
    val SAVEFILE = GameCommandSpec(GameCommandWires.SAVEFILE)
    @JvmField
    val SAVEPAGE = GameCommandSpec(GameCommandWires.SAVEPAGE)
    @JvmField
    val SAVEPORTNOTE = GameCommandSpec(GameCommandWires.SAVEPORTNOTE)
    @JvmField
    val SELLFILE = GameCommandSpec(GameCommandWires.SELLFILE)
    @JvmField
    val SELLFILEMULTI = GameCommandSpec(GameCommandWires.SELLFILEMULTI)
    @JvmField
    val SETDEFAULTPORT = GameCommandSpec(GameCommandWires.SETDEFAULTPORT)
    @JvmField
    val SETDUMMYPORT = GameCommandSpec(GameCommandWires.SETDUMMYPORT)
    @JvmField
    val SETFILEDESCRIPTION = GameCommandSpec(GameCommandWires.SETFILEDESCRIPTION)
    @JvmField
    val SETFILEPRICE = GameCommandSpec(GameCommandWires.SETFILEPRICE)
    @JvmField
    val SETFTPPASSWORD = GameCommandSpec(GameCommandWires.SETFTPPASSWORD)
    @JvmField
    val SETPREFERENCES = GameCommandSpec(GameCommandWires.SETPREFERENCES)
    @JvmField
    val SETWATCHNOTE = GameCommandSpec(GameCommandWires.SETWATCHNOTE)
    @JvmField
    val SETWATCHOBSERVEDPORTS = GameCommandSpec(GameCommandWires.SETWATCHOBSERVEDPORTS)
    @JvmField
    val SETWATCHONOFF = GameCommandSpec(GameCommandWires.SETWATCHONOFF)
    @JvmField
    val SETWATCHQUANTITY = GameCommandSpec(GameCommandWires.SETWATCHQUANTITY)
    @JvmField
    val SETWATCHSEARCHFIREWALL = GameCommandSpec(GameCommandWires.SETWATCHSEARCHFIREWALL)
    @JvmField
    val SUBMIT = GameCommandSpec(GameCommandWires.SUBMIT)
    @JvmField
    val TRANSFER = GameCommandSpec(GameCommandWires.TRANSFER)
    @JvmField
    val UNINSTALLPORT = GameCommandSpec(GameCommandWires.UNINSTALLPORT)
    @JvmField
    val UNLOCK = GameCommandSpec(GameCommandWires.UNLOCK)
    @JvmField
    val VOTE = GameCommandSpec(GameCommandWires.VOTE)
    @JvmField
    val WITHDRAW = GameCommandSpec(GameCommandWires.WITHDRAW)
    @JvmField
    val ADDSHOWCHOICES = GameCommandSpec(GameCommandWires.ADDSHOWCHOICES)
    @JvmField
    val ATTACK = GameCommandSpec(GameCommandWires.ATTACK)
    @JvmField
    val ATTACKCONTINUE = GameCommandSpec(GameCommandWires.ATTACKCONTINUE)
    @JvmField
    val ATTACKFINALIZE = GameCommandSpec(GameCommandWires.ATTACKFINALIZE)
    @JvmField
    val ATTACKINITIALIZE = GameCommandSpec(GameCommandWires.ATTACKINITIALIZE)
    @JvmField
    val ATTACKXP = GameCommandSpec(GameCommandWires.ATTACKXP)
    @JvmField
    val BANK = GameCommandSpec(GameCommandWires.BANK)
    @JvmField
    val BANKXP = GameCommandSpec(GameCommandWires.BANKXP)
    @JvmField
    val BOUNTYHTTP = GameCommandSpec(GameCommandWires.BOUNTYHTTP)
    @JvmField
    val CANCELATTACK = GameCommandSpec(GameCommandWires.CANCELATTACK)
    @JvmField
    val CHALLENGERESULTS = GameCommandSpec(GameCommandWires.CHALLENGERESULTS)
    @JvmField
    val CHANGENETWORK2 = GameCommandSpec(GameCommandWires.CHANGENETWORK2)
    @JvmField
    val CHECKBOUNTY = GameCommandSpec(GameCommandWires.CHECKBOUNTY)
    @JvmField
    val CODE = GameCommandSpec(GameCommandWires.CODE)
    @JvmField
    val COMMODITY = GameCommandSpec(GameCommandWires.COMMODITY)
    @JvmField
    val COMPLETETASK = GameCommandSpec(GameCommandWires.COMPLETETASK)
    @JvmField
    val CONTINUEPURCHASE = GameCommandSpec(GameCommandWires.CONTINUEPURCHASE)
    @JvmField
    val DAILYPAYSET = GameCommandSpec(GameCommandWires.DAILYPAYSET)
    @JvmField
    val DAMAGE = GameCommandSpec(GameCommandWires.DAMAGE)
    @JvmField
    val DELETELOG = GameCommandSpec(GameCommandWires.DELETELOG)
    @JvmField
    val DELIVEREDDIRECTORY = GameCommandSpec(GameCommandWires.DELIVEREDDIRECTORY)
    @JvmField
    val DESTROY_WATCH = GameCommandSpec(GameCommandWires.DESTROY_WATCH)
    @JvmField
    val EDIT_LOGS = GameCommandSpec(GameCommandWires.EDIT_LOGS)
    @JvmField
    val EMPTY_PETTY_CASH = GameCommandSpec(GameCommandWires.EMPTY_PETTY_CASH)
    @JvmField
    val EXCHANGECOMMODITY = GameCommandSpec(GameCommandWires.EXCHANGECOMMODITY)
    @JvmField
    val EXCHANGEFILE = GameCommandSpec(GameCommandWires.EXCHANGEFILE)
    @JvmField
    val FINALIZEPUT = GameCommandSpec(GameCommandWires.FINALIZEPUT)
    @JvmField
    val FINISHQUEST = GameCommandSpec(GameCommandWires.FINISHQUEST)
    @JvmField
    val FIREWALLXP = GameCommandSpec(GameCommandWires.FIREWALLXP)
    @JvmField
    val FREEZE = GameCommandSpec(GameCommandWires.FREEZE)
    @JvmField
    val GIVEACCESS = GameCommandSpec(GameCommandWires.GIVEACCESS)
    @JvmField
    val GIVECOMMODITY = GameCommandSpec(GameCommandWires.GIVECOMMODITY)
    @JvmField
    val GIVEEXPERIENCE = GameCommandSpec(GameCommandWires.GIVEEXPERIENCE)
    @JvmField
    val GIVEFILE = GameCommandSpec(GameCommandWires.GIVEFILE)
    @JvmField
    val GIVEQUEST = GameCommandSpec(GameCommandWires.GIVEQUEST)
    @JvmField
    val GIVETASK = GameCommandSpec(GameCommandWires.GIVETASK)
    @JvmField
    val HEAL = GameCommandSpec(GameCommandWires.HEAL)
    @JvmField
    val HTTPXP = GameCommandSpec(GameCommandWires.HTTPXP)
    @JvmField
    val INSTALL_SCRIPT = GameCommandSpec(GameCommandWires.INSTALL_SCRIPT)
    @JvmField
    val LAUNCH_NETWORK_ATTACK = GameCommandSpec(GameCommandWires.LAUNCH_NETWORK_ATTACK)
    @JvmField
    val LOGMESSAGE = GameCommandSpec(GameCommandWires.LOGMESSAGE)
    @JvmField
    val MESSAGE = GameCommandSpec(GameCommandWires.MESSAGE)
    @JvmField
    val MINE = GameCommandSpec(GameCommandWires.MINE)
    @JvmField
    val MININGDAMAGEUPDATE = GameCommandSpec(GameCommandWires.MININGDAMAGEUPDATE)
    @JvmField
    val NULL = GameCommandSpec(GameCommandWires.NULL)
    @JvmField
    val OPPONENTUPDATE = GameCommandSpec(GameCommandWires.OPPONENTUPDATE)
    @JvmField
    val PETTYCASH = GameCommandSpec(GameCommandWires.PETTYCASH)
    @JvmField
    val PING = GameCommandSpec(GameCommandWires.PING)
    @JvmField
    val QUESTINFORMATION = GameCommandSpec(GameCommandWires.QUESTINFORMATION)
    @JvmField
    val REPAIRXP = GameCommandSpec(GameCommandWires.REPAIRXP)
    @JvmField
    val REQUESTATTACKDEFAULT = GameCommandSpec(GameCommandWires.REQUESTATTACKDEFAULT)
    @JvmField
    val REQUESTFTPUPDATE = GameCommandSpec(GameCommandWires.REQUESTFTPUPDATE)
    @JvmField
    val REQUESTINSTALLSCRIPT = GameCommandSpec(GameCommandWires.REQUESTINSTALLSCRIPT)
    @JvmField
    val REQUESTNETWORKHOP = GameCommandSpec(GameCommandWires.REQUESTNETWORKHOP)
    @JvmField
    val REQUESTTRIGGERNOTE = GameCommandSpec(GameCommandWires.REQUESTTRIGGERNOTE)
    @JvmField
    val SCAN = GameCommandSpec(GameCommandWires.SCAN)
    @JvmField
    val SCANSUCCESS = GameCommandSpec(GameCommandWires.SCANSUCCESS)
    @JvmField
    val SCANXP = GameCommandSpec(GameCommandWires.SCANXP)
    @JvmField
    val SENDEMAIL = GameCommandSpec(GameCommandWires.SENDEMAIL)
    @JvmField
    val SENDFACEBOOK = GameCommandSpec(GameCommandWires.SENDFACEBOOK)
    @JvmField
    val SETTASK = GameCommandSpec(GameCommandWires.SETTASK)
    @JvmField
    val TAKECOMMODITY = GameCommandSpec(GameCommandWires.TAKECOMMODITY)
    @JvmField
    val TAKEFILE = GameCommandSpec(GameCommandWires.TAKEFILE)
    @JvmField
    val TAKEFILE2 = GameCommandSpec(GameCommandWires.TAKEFILE2)
    @JvmField
    val TAKEMONEY = GameCommandSpec(GameCommandWires.TAKEMONEY)
    @JvmField
    val WATCHXP = GameCommandSpec(GameCommandWires.WATCHXP)
    @JvmField
    val WEBPAGE = GameCommandSpec(GameCommandWires.WEBPAGE)
    @JvmField
    val ZOMBIEATTACK = GameCommandSpec(GameCommandWires.ZOMBIEATTACK)

    @JvmField
    val ALL: List<GameCommandSpec> = listOf(
        CHANGEDAILYPAY,
        CHANGENETWORK,
        CHANGEWATCHPORT,
        CHANGEWATCHTYPE,
        CLUEDATA,
        COMPILEFILE,
        CREATEFOLDER,
        DECOMPILEFILE,
        DELETEFILE,
        DELETEFIREWALL,
        DELETEFOLDER,
        DELETELOGS,
        DELETEMULTI,
        DELETEWATCH,
        DEPOSIT,
        DOCHALLENGE,
        EMPTYPETTYCASH,
        EXIT,
        FACEBOOKDEPOSIT,
        FACEBOOKTRANSFER,
        FACEBOOKUPDATE,
        FACEBOOKWITHDRAW,
        FETCHPORTS,
        FETCHWATCHES,
        FINALIZECANCELLED,
        GET,
        HACKTENDO_ACTIVATE,
        HACKTENDO_TARGET,
        HEALPORT,
        INSTALLAPPLICATION,
        INSTALLEQUIPMENT,
        INSTALLFIREWALL,
        INSTALLWATCH,
        MAKEBOUNTY,
        MALGET,
        PEEKCODE,
        PEEKLOGS,
        PORTONOFF,
        PUT,
        REPAIREQUIPMENT,
        REPLACEAPPLICATION,
        REQUESTATTACK,
        REQUESTCANCELATTACK,
        REQUESTDIRECTORY,
        REQUESTEQUIPMENT,
        REQUESTFILE,
        REQUESTGAME,
        REQUESTPAGE,
        REQUESTPURCHASE,
        REQUESTSAVE,
        REQUESTSCAN,
        REQUESTSECONDARYDIRECTORY,
        REQUESTTASK,
        REQUESTTRIGGER,
        REQUESTWEBPAGE,
        REQUESTZOMBIEATTACK,
        REQUESTZOMBIECANCELATTACK,
        SAVEFILE,
        SAVEPAGE,
        SAVEPORTNOTE,
        SELLFILE,
        SELLFILEMULTI,
        SETDEFAULTPORT,
        SETDUMMYPORT,
        SETFILEDESCRIPTION,
        SETFILEPRICE,
        SETFTPPASSWORD,
        SETPREFERENCES,
        SETWATCHNOTE,
        SETWATCHOBSERVEDPORTS,
        SETWATCHONOFF,
        SETWATCHQUANTITY,
        SETWATCHSEARCHFIREWALL,
        SUBMIT,
        TRANSFER,
        UNINSTALLPORT,
        UNLOCK,
        VOTE,
        WITHDRAW,
        ADDSHOWCHOICES,
        ATTACK,
        ATTACKCONTINUE,
        ATTACKFINALIZE,
        ATTACKINITIALIZE,
        ATTACKXP,
        BANK,
        BANKXP,
        BOUNTYHTTP,
        CANCELATTACK,
        CHALLENGERESULTS,
        CHANGENETWORK2,
        CHECKBOUNTY,
        CODE,
        COMMODITY,
        COMPLETETASK,
        CONTINUEPURCHASE,
        DAILYPAYSET,
        DAMAGE,
        DELETELOG,
        DELIVEREDDIRECTORY,
        DESTROY_WATCH,
        EDIT_LOGS,
        EMPTY_PETTY_CASH,
        EXCHANGECOMMODITY,
        EXCHANGEFILE,
        FINALIZEPUT,
        FINISHQUEST,
        FIREWALLXP,
        FREEZE,
        GIVEACCESS,
        GIVECOMMODITY,
        GIVEEXPERIENCE,
        GIVEFILE,
        GIVEQUEST,
        GIVETASK,
        HEAL,
        HTTPXP,
        INSTALL_SCRIPT,
        LAUNCH_NETWORK_ATTACK,
        LOGMESSAGE,
        MESSAGE,
        MINE,
        MININGDAMAGEUPDATE,
        NULL,
        OPPONENTUPDATE,
        PETTYCASH,
        PING,
        QUESTINFORMATION,
        REPAIRXP,
        REQUESTATTACKDEFAULT,
        REQUESTFTPUPDATE,
        REQUESTINSTALLSCRIPT,
        REQUESTNETWORKHOP,
        REQUESTTRIGGERNOTE,
        SCAN,
        SCANSUCCESS,
        SCANXP,
        SENDEMAIL,
        SENDFACEBOOK,
        SETTASK,
        TAKECOMMODITY,
        TAKEFILE,
        TAKEFILE2,
        TAKEMONEY,
        WATCHXP,
        WEBPAGE,
        ZOMBIEATTACK
    )

    @JvmField
    val BY_WIRE_NAME: Map<String, GameCommandSpec> = ALL.associateBy { it.wireName }

    @JvmStatic
    fun byWireName(wireName: String): GameCommandSpec? = BY_WIRE_NAME[wireName]

    @JvmStatic
    fun require(wireName: String): GameCommandSpec =
        BY_WIRE_NAME[wireName] ?: error("Unknown game command: $wireName")
}
