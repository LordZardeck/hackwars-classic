package com.hackwars.rpc

object GameFunctions {
    @JvmField
    val CHANGEDAILYPAY =
        GameFunctionSpec(GameCommands.CHANGEDAILYPAY, ChangeDailyPay::class.java, ChangeDailyPay::fromRpc)
    @JvmField
    val CHANGENETWORK = GameFunctionSpec(GameCommands.CHANGENETWORK, ChangeNetwork::class.java, ChangeNetwork::fromRpc)
    @JvmField
    val CHANGEWATCHPORT =
        GameFunctionSpec(GameCommands.CHANGEWATCHPORT, ChangeWatchPort::class.java, ChangeWatchPort::fromRpc)
    @JvmField
    val CHANGEWATCHTYPE =
        GameFunctionSpec(GameCommands.CHANGEWATCHTYPE, ChangeWatchType::class.java, ChangeWatchType::fromRpc)
    @JvmField
    val CLUEDATA = GameFunctionSpec(GameCommands.CLUEDATA, ClueData::class.java, ClueData::fromRpc)
    @JvmField
    val COMPILEFILE = GameFunctionSpec(GameCommands.COMPILEFILE, CompileFile::class.java, CompileFile::fromRpc)
    @JvmField
    val CREATEFOLDER = GameFunctionSpec(GameCommands.CREATEFOLDER, CreateFolder::class.java, CreateFolder::fromRpc)
    @JvmField
    val DECOMPILEFILE = GameFunctionSpec(GameCommands.DECOMPILEFILE, DecompileFile::class.java, DecompileFile::fromRpc)
    @JvmField
    val DELETEFILE = GameFunctionSpec(GameCommands.DELETEFILE, DeleteFile::class.java, DeleteFile::fromRpc)
    @JvmField
    val DELETEFIREWALL =
        GameFunctionSpec(GameCommands.DELETEFIREWALL, DeleteFirewall::class.java, DeleteFirewall::fromRpc)
    @JvmField
    val DELETEFOLDER = GameFunctionSpec(GameCommands.DELETEFOLDER, DeleteFolder::class.java, DeleteFolder::fromRpc)
    @JvmField
    val DELETELOGS = GameFunctionSpec(GameCommands.DELETELOGS, DeleteLogs::class.java, DeleteLogs::fromRpc)
    @JvmField
    val DELETEMULTI = GameFunctionSpec(GameCommands.DELETEMULTI, DeleteMulti::class.java, DeleteMulti::fromRpc)
    @JvmField
    val DELETEWATCH = GameFunctionSpec(GameCommands.DELETEWATCH, DeleteWatch::class.java, DeleteWatch::fromRpc)
    @JvmField
    val DEPOSIT = GameFunctionSpec(GameCommands.DEPOSIT, Deposit::class.java, Deposit::fromRpc)
    @JvmField
    val DOCHALLENGE = GameFunctionSpec(GameCommands.DOCHALLENGE, DoChallenge::class.java, DoChallenge::fromRpc)
    @JvmField
    val EMPTYPETTYCASH =
        GameFunctionSpec(GameCommands.EMPTYPETTYCASH, EmptyPettyCash::class.java, EmptyPettyCash::fromRpc)
    @JvmField
    val EXIT = GameFunctionSpec(GameCommands.EXIT, Exit::class.java, Exit::fromRpc)
    @JvmField
    val FACEBOOKDEPOSIT =
        GameFunctionSpec(GameCommands.FACEBOOKDEPOSIT, FacebookDeposit::class.java, FacebookDeposit::fromRpc)
    @JvmField
    val FACEBOOKTRANSFER =
        GameFunctionSpec(GameCommands.FACEBOOKTRANSFER, FacebookTransfer::class.java, FacebookTransfer::fromRpc)
    @JvmField
    val FACEBOOKUPDATE =
        GameFunctionSpec(GameCommands.FACEBOOKUPDATE, FacebookUpdate::class.java, FacebookUpdate::fromRpc)
    @JvmField
    val FACEBOOKWITHDRAW =
        GameFunctionSpec(GameCommands.FACEBOOKWITHDRAW, FacebookWithdraw::class.java, FacebookWithdraw::fromRpc)
    @JvmField
    val FETCHPORTS = GameFunctionSpec(GameCommands.FETCHPORTS, FetchPorts::class.java, FetchPorts::fromRpc)
    @JvmField
    val FETCHWATCHES = GameFunctionSpec(GameCommands.FETCHWATCHES, FetchWatches::class.java, FetchWatches::fromRpc)
    @JvmField
    val FINALIZECANCELLED =
        GameFunctionSpec(GameCommands.FINALIZECANCELLED, FinalizeCancelled::class.java, FinalizeCancelled::fromRpc)
    @JvmField
    val GET = GameFunctionSpec(GameCommands.GET, Get::class.java, Get::fromRpc)
    @JvmField
    val HACKTENDO_ACTIVATE =
        GameFunctionSpec(GameCommands.HACKTENDO_ACTIVATE, HacktendoActivate::class.java, HacktendoActivate::fromRpc)
    @JvmField
    val HACKTENDO_TARGET =
        GameFunctionSpec(GameCommands.HACKTENDO_TARGET, HacktendoTarget::class.java, HacktendoTarget::fromRpc)
    @JvmField
    val HEALPORT = GameFunctionSpec(GameCommands.HEALPORT, HealPort::class.java, HealPort::fromRpc)
    @JvmField
    val INSTALLAPPLICATION =
        GameFunctionSpec(GameCommands.INSTALLAPPLICATION, InstallApplication::class.java, InstallApplication::fromRpc)
    @JvmField
    val INSTALLEQUIPMENT =
        GameFunctionSpec(GameCommands.INSTALLEQUIPMENT, InstallEquipment::class.java, InstallEquipment::fromRpc)
    @JvmField
    val INSTALLFIREWALL =
        GameFunctionSpec(GameCommands.INSTALLFIREWALL, InstallFirewall::class.java, InstallFirewall::fromRpc)
    @JvmField
    val INSTALLWATCH = GameFunctionSpec(GameCommands.INSTALLWATCH, InstallWatch::class.java, InstallWatch::fromRpc)
    @JvmField
    val MAKEBOUNTY = GameFunctionSpec(GameCommands.MAKEBOUNTY, MakeBounty::class.java, MakeBounty::fromRpc)
    @JvmField
    val MALGET = GameFunctionSpec(GameCommands.MALGET, MalGet::class.java, MalGet::fromRpc)
    @JvmField
    val PEEKCODE = GameFunctionSpec(GameCommands.PEEKCODE, PeekCode::class.java, PeekCode::fromRpc)
    @JvmField
    val PEEKLOGS = GameFunctionSpec(GameCommands.PEEKLOGS, PeekLogs::class.java, PeekLogs::fromRpc)
    @JvmField
    val PORTONOFF = GameFunctionSpec(GameCommands.PORTONOFF, PortOnOff::class.java, PortOnOff::fromRpc)
    @JvmField
    val PUT = GameFunctionSpec(GameCommands.PUT, Put::class.java, Put::fromRpc)
    @JvmField
    val REPAIREQUIPMENT =
        GameFunctionSpec(GameCommands.REPAIREQUIPMENT, RepairEquipment::class.java, RepairEquipment::fromRpc)
    @JvmField
    val REPLACEAPPLICATION =
        GameFunctionSpec(GameCommands.REPLACEAPPLICATION, ReplaceApplication::class.java, ReplaceApplication::fromRpc)
    @JvmField
    val REQUESTATTACK = GameFunctionSpec(GameCommands.REQUESTATTACK, RequestAttack::class.java, RequestAttack::fromRpc)
    @JvmField
    val REQUESTCANCELATTACK = GameFunctionSpec(
        GameCommands.REQUESTCANCELATTACK,
        RequestCancelAttack::class.java,
        RequestCancelAttack::fromRpc
    )
    @JvmField
    val REQUESTDIRECTORY =
        GameFunctionSpec(GameCommands.REQUESTDIRECTORY, RequestDirectory::class.java, RequestDirectory::fromRpc)
    @JvmField
    val REQUESTEQUIPMENT =
        GameFunctionSpec(GameCommands.REQUESTEQUIPMENT, RequestEquipment::class.java, RequestEquipment::fromRpc)
    @JvmField
    val REQUESTFILE = GameFunctionSpec(GameCommands.REQUESTFILE, RequestFile::class.java, RequestFile::fromRpc)
    @JvmField
    val REQUESTGAME = GameFunctionSpec(GameCommands.REQUESTGAME, RequestGame::class.java, RequestGame::fromRpc)
    @JvmField
    val REQUESTPAGE = GameFunctionSpec(GameCommands.REQUESTPAGE, RequestPage::class.java, RequestPage::fromRpc)
    @JvmField
    val REQUESTPURCHASE =
        GameFunctionSpec(GameCommands.REQUESTPURCHASE, RequestPurchase::class.java, RequestPurchase::fromRpc)
    @JvmField
    val REQUESTSAVE = GameFunctionSpec(GameCommands.REQUESTSAVE, RequestSave::class.java, RequestSave::fromRpc)
    @JvmField
    val REQUESTSCAN = GameFunctionSpec(GameCommands.REQUESTSCAN, RequestScan::class.java, RequestScan::fromRpc)
    @JvmField
    val REQUESTSECONDARYDIRECTORY = GameFunctionSpec(
        GameCommands.REQUESTSECONDARYDIRECTORY,
        RequestSecondaryDirectory::class.java,
        RequestSecondaryDirectory::fromRpc
    )
    @JvmField
    val REQUESTTASK = GameFunctionSpec(GameCommands.REQUESTTASK, RequestTask::class.java, RequestTask::fromRpc)
    @JvmField
    val REQUESTTRIGGER =
        GameFunctionSpec(GameCommands.REQUESTTRIGGER, RequestTrigger::class.java, RequestTrigger::fromRpc)
    @JvmField
    val REQUESTWEBPAGE =
        GameFunctionSpec(GameCommands.REQUESTWEBPAGE, RequestWebpage::class.java, RequestWebpage::fromRpc)
    @JvmField
    val REQUESTZOMBIEATTACK = GameFunctionSpec(
        GameCommands.REQUESTZOMBIEATTACK,
        RequestZombieAttack::class.java,
        RequestZombieAttack::fromRpc
    )
    @JvmField
    val REQUESTZOMBIECANCELATTACK = GameFunctionSpec(
        GameCommands.REQUESTZOMBIECANCELATTACK,
        RequestZombieCancelAttack::class.java,
        RequestZombieCancelAttack::fromRpc
    )
    @JvmField
    val SAVEFILE = GameFunctionSpec(GameCommands.SAVEFILE, SaveFile::class.java, SaveFile::fromRpc)
    @JvmField
    val SAVEPAGE = GameFunctionSpec(GameCommands.SAVEPAGE, SavePage::class.java, SavePage::fromRpc)
    @JvmField
    val SAVEPORTNOTE = GameFunctionSpec(GameCommands.SAVEPORTNOTE, SavePortNote::class.java, SavePortNote::fromRpc)
    @JvmField
    val SELLFILE = GameFunctionSpec(GameCommands.SELLFILE, SellFile::class.java, SellFile::fromRpc)
    @JvmField
    val SELLFILEMULTI = GameFunctionSpec(GameCommands.SELLFILEMULTI, SellFileMulti::class.java, SellFileMulti::fromRpc)
    @JvmField
    val SETDEFAULTPORT =
        GameFunctionSpec(GameCommands.SETDEFAULTPORT, SetDefaultPort::class.java, SetDefaultPort::fromRpc)
    @JvmField
    val SETDUMMYPORT = GameFunctionSpec(GameCommands.SETDUMMYPORT, SetDummyPort::class.java, SetDummyPort::fromRpc)
    @JvmField
    val SETFILEDESCRIPTION =
        GameFunctionSpec(GameCommands.SETFILEDESCRIPTION, SetFileDescription::class.java, SetFileDescription::fromRpc)
    @JvmField
    val SETFILEPRICE = GameFunctionSpec(GameCommands.SETFILEPRICE, SetFilePrice::class.java, SetFilePrice::fromRpc)
    @JvmField
    val SETFTPPASSWORD =
        GameFunctionSpec(GameCommands.SETFTPPASSWORD, SetFtpPassword::class.java, SetFtpPassword::fromRpc)
    @JvmField
    val SETPREFERENCES =
        GameFunctionSpec(GameCommands.SETPREFERENCES, SetPreferences::class.java, SetPreferences::fromRpc)
    @JvmField
    val SETWATCHNOTE = GameFunctionSpec(GameCommands.SETWATCHNOTE, SetWatchNote::class.java, SetWatchNote::fromRpc)
    @JvmField
    val SETWATCHOBSERVEDPORTS = GameFunctionSpec(
        GameCommands.SETWATCHOBSERVEDPORTS,
        SetWatchObservedPorts::class.java,
        SetWatchObservedPorts::fromRpc
    )
    @JvmField
    val SETWATCHONOFF = GameFunctionSpec(GameCommands.SETWATCHONOFF, SetWatchOnOff::class.java, SetWatchOnOff::fromRpc)
    @JvmField
    val SETWATCHQUANTITY =
        GameFunctionSpec(GameCommands.SETWATCHQUANTITY, SetWatchQuantity::class.java, SetWatchQuantity::fromRpc)
    @JvmField
    val SETWATCHSEARCHFIREWALL = GameFunctionSpec(
        GameCommands.SETWATCHSEARCHFIREWALL,
        SetWatchSearchFirewall::class.java,
        SetWatchSearchFirewall::fromRpc
    )
    @JvmField
    val SUBMIT = GameFunctionSpec(GameCommands.SUBMIT, Submit::class.java, Submit::fromRpc)
    @JvmField
    val TRANSFER = GameFunctionSpec(GameCommands.TRANSFER, Transfer::class.java, Transfer::fromRpc)
    @JvmField
    val UNINSTALLPORT = GameFunctionSpec(GameCommands.UNINSTALLPORT, UninstallPort::class.java, UninstallPort::fromRpc)
    @JvmField
    val UNLOCK = GameFunctionSpec(GameCommands.UNLOCK, Unlock::class.java, Unlock::fromRpc)
    @JvmField
    val VOTE = GameFunctionSpec(GameCommands.VOTE, Vote::class.java, Vote::fromRpc)
    @JvmField
    val WITHDRAW = GameFunctionSpec(GameCommands.WITHDRAW, Withdraw::class.java, Withdraw::fromRpc)

    @JvmField
    val ALL: List<GameFunctionSpec> = listOf(
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
        WITHDRAW
    )

    @JvmField
    val BY_WIRE_NAME: Map<String, GameFunctionSpec> = ALL.associateBy { it.wireName }

    @JvmStatic
    fun byWireName(wireName: String): GameFunctionSpec? = BY_WIRE_NAME[wireName]
}
