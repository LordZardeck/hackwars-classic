package game

import java.util.ArrayList
import java.util.regex.Matcher

class MessageHandler(private val computer: Computer) {
    fun addMessage(messageArray: Array<out Any?>?, parameters: Array<out Any?>?) {
        addMessage(messageArray, parameters, null)
    }

    fun addMessage(
        messageArray: Array<out Any?>?,
        parameters: Array<out Any?>?,
        portInfo: Array<out Any?>?
    ) {
        val resolvedMessageArray = messageArray!!
        var message = resolvedMessageArray[0] as String
        val type = resolvedMessageArray[1] as Int
        val send = arrayOfNulls<Any>(4)
        send[1] = type
        if (parameters != null) {
            for (i in parameters.indices) {
                message = message.replace(
                    Regex("_" + i + "_"),
                    Matcher.quoteReplacement("" + parameters[i])
                )
            }
        }
        send[0] = message
        send[2] = if (resolvedMessageArray.size == 3) resolvedMessageArray[2] else ""
        send[3] = portInfo
        @Suppress("UNCHECKED_CAST")
        val messages = computer.getMessages() as ArrayList<Any?>
        messages.add(send)
    }

    companion object {
        @JvmField
        val GAME_MESSAGE = 0

        @JvmField
        val POPUP_ERROR = 1

        @JvmField
        val POPUP_MESSAGE = 2

        @JvmField
        val ATTACK_MESSAGE = 3

        @JvmField
        val REDIRECT_MESSAGE = 4

        // Keep option keys local so this shared class does not depend on gui.OptionPanel.
        private const val APP_REPLACED_KEY = "appreplaced"
        private const val HEALING_KEY = "healing"
        private const val CARD_REPAIRED_KEY = "cardrepaired"
        private const val CARD_REPLACED_KEY = "cardreplaced"
        private const val COMMOD_TO_FILE_KEY = "commodtofile"
        private const val FILE_TO_COMMOD_KEY = "filetocommod"
        private const val TRANSFER_TO_KEY = "transferto"
        private const val TRANSFER_FROM_KEY = "transferfrom"
        private const val FILE_PURCHASED_KEY = "filepurchased"
        private const val VOTE_SUCCESSFUL_KEY = "votesuccessful"
        private const val FIREWALL_REPLACED_KEY = "firewallreplaced"
        private const val FIREWALL_REMOVED_KEY = "firewallremoved"

        @JvmField val ACTIVE_BANK_NOT_FOUND = arrayOf<Any?>("Transaction failed, make sure you have an non-dummy banking port turned on.", GAME_MESSAGE)
        @JvmField val APPLICATION_NOT_FOUND = arrayOf<Any?>("Application not found on HD.", GAME_MESSAGE)
        @JvmField val ATTACK_EXCEEDED_TIMEOUT = arrayOf<Any?>("Attack exceeded maximum timeout.", ATTACK_MESSAGE)
        @JvmField val ATTACK_FAIL_OVERHEATED = arrayOf<Any?>("Can't attack when port is overheated.", POPUP_ERROR)
        @JvmField val ATTACK_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("You must have \$10 in your petty cash to start an attack.", POPUP_ERROR)
        @JvmField val ATTACK_FAIL_WRONG_NETWORK = arrayOf<Any?>("You cannot attack _0_ they are on the network \"_1_\".", POPUP_ERROR)
        @JvmField val ATTACK_FAIL_NOOB = arrayOf<Any?>("Stop picking on noobs!", POPUP_ERROR)
        @JvmField val ATTACK_CANCELLED = arrayOf<Any?>("Your attack was canceled.", ATTACK_MESSAGE)

        @JvmField val BOUNTY_COMPLETED = arrayOf<Any?>("Received _0_ reward from bounty.", POPUP_MESSAGE)
        @JvmField val BOUNTY_FAILED_ALREADY_COMPLETED = arrayOf<Any?>("Another player already completed this bounty.", POPUP_ERROR)
        @JvmField val BOUNTY_STEP_COMPLETED = arrayOf<Any?>("A step in a bounty has been completed.", GAME_MESSAGE)

        @JvmField val CHALLENGE_START = arrayOf<Any?>("Attempting Challenge ID _0_", GAME_MESSAGE)
        @JvmField val CHALLENGE_NO_FILE = arrayOf<Any?>("You do not currently have a challenge with this ID on your HD.", POPUP_ERROR)
        @JvmField val CHALLENGE_RUNNING_ATTEMPT = arrayOf<Any?>("Running attempt _0_", GAME_MESSAGE)
        @JvmField val CHALLENGE_COMPLETED = arrayOf<Any?>("Challenge successfully completed.", POPUP_MESSAGE)
        @JvmField val CHALLENGE_FAILED = arrayOf<Any?>("Challenge failed try again.", POPUP_ERROR)
        @JvmField val CHANGE_DAILY_PAY_FAIL_WRONG_TYPE = arrayOf<Any?>("You can only change daily pay on an HTTP port.", POPUP_ERROR)
        @JvmField val CHANGE_DAILY_PAY_FAIL_BOUNTY = arrayOf<Any?>("You cannot immediately take back over an HTTP attacked as part of a bounty.", POPUP_ERROR)
        @JvmField val CHANGE_DAILY_PAY_SUCCESS = arrayOf<Any?>("Daily pay successfully changed.", ATTACK_MESSAGE)
        @JvmField val CHANGE_DAILY_PAY_SUCCESS_GAME = arrayOf<Any?>("Daily pay successfully changed.", GAME_MESSAGE)
        @JvmField val CHANGE_DAILY_PAY_FAIL_ALREADY_CONTROLLED = arrayOf<Any?>("You already controlled this HTTP.", ATTACK_MESSAGE)
        @JvmField val CHANGE_DAILY_PAY_FAIL_FIREWALL = arrayOf<Any?>("_0_'s firewall has caused the change daily pay to fail.", POPUP_MESSAGE)
        @JvmField val CHANGE_NETWORK_FAIL_ALREADY_ON = arrayOf<Any?>("You are already on _0_.", POPUP_ERROR)
        @JvmField val CHANGE_NETWORK_FAIL_TIMEOUT = arrayOf<Any?>("You have not been on _0_ long enough to change networks.", POPUP_ERROR)
        @JvmField val CHANGE_NETWORK_FAIL_JAILED = arrayOf<Any?>("You are in jail. Network change prohibited from _0_.", POPUP_ERROR)
        @JvmField val CHANGE_NETWORK_SUCCESS = arrayOf<Any?>("Moved to network \"_0_\"", POPUP_MESSAGE)
        @JvmField val COMPILE_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("You do not have enough money to compile the requested script.", POPUP_ERROR)
        @JvmField val COMPILE_FAIL_HD_FULL = arrayOf<Any?>("You do not have enough disk space to compile this file.", POPUP_ERROR)
        @JvmField val COULD_NOT_EXECUTE_APPLICATION = arrayOf<Any?>("Could not execute application on port _0_. This could mean a program is missing on a default port.", GAME_MESSAGE)
        @JvmField val COMPUTER_OVERHEATED = arrayOf<Any?>("Your computer just overheated!", POPUP_ERROR)
        @JvmField val CPU_TOO_HIGH = arrayOf<Any?>("Your CPU cannot handle that many processes running.", POPUP_ERROR)

        @JvmField val DELETE_FAIL_NON_EMPTY_FOLDER = arrayOf<Any?>("Can not delete non-empty folders.", POPUP_ERROR)
        @JvmField val DELETE_LOGS_SUCCESS = arrayOf<Any?>("Logs successfully deleted.", ATTACK_MESSAGE)
        @JvmField val DESTROY_WATCHES_SUCCESS = arrayOf<Any?>("Watches successfully destroyed.", ATTACK_MESSAGE)

        @JvmField val EDIT_LOGS_SUCCESS = arrayOf<Any?>("Logs successfully edited.", ATTACK_MESSAGE)
        @JvmField val EMPTY_PETTY_FAIL_WRONG_TYPE = arrayOf<Any?>("You can only empty petty cash on a Banking port.", ATTACK_MESSAGE)
        @JvmField val EMPTY_PETTY_FAIL_FIREWALL = arrayOf<Any?>("_0_'s firewall has caused the empty petty cash to fail.", POPUP_MESSAGE)
        @JvmField val EMPTY_PETTY_SUCCESS = arrayOf<Any?>("You have successfully stolen _0_.", ATTACK_MESSAGE)
        @JvmField val EMPTY_PETTY_SUCCESS_GAME = arrayOf<Any?>("You have successfully stolen _0_ from _1_.", GAME_MESSAGE)
        @JvmField val EMPTY_PETTY_FAIL_NO_ACTIVE_BANK = arrayOf<Any?>("Could not empty petty cash from _0_, you don't have a non-dummy bank port turned on.", ATTACK_MESSAGE)
        @JvmField val EQUIP_FAIL_WRONG_TYPE = arrayOf<Any?>("Can not equip this type of hardware in this slot.", POPUP_ERROR)
        @JvmField val EQUIP_FAIL_HD_FULL = arrayOf<Any?>("Could not install card: Equipping this card would put you over your maximum file limit.", POPUP_ERROR)
        @JvmField val EQUIP_FAIL_CPU_RESTRICTIONS = arrayOf<Any?>("Could not install card: Equpping this card would put you over your CPU limit and overheat you.", POPUP_ERROR)
        @JvmField val EQUIP_FAIL_WATCH_RESTRICTIONS = arrayOf<Any?>("Could not install card: Equipping this card would put you over your maximum number of watches.", POPUP_ERROR)
        @JvmField val EQUIP_SUCCESS = arrayOf<Any?>("Card successfully replaced.", POPUP_MESSAGE, CARD_REPLACED_KEY)
        @JvmField val EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT = arrayOf<Any?>("Failed to exchange commodity. Make sure you have a non-dummy bank port on.", POPUP_ERROR)
        @JvmField val EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_COMMODITY = arrayOf<Any?>("Failed to exchange commodity. You do not have that much _0_.", POPUP_ERROR)
        @JvmField val EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("Failed to exchange commodity. You do not have that much money in your petty cash.", POPUP_ERROR)
        @JvmField val EXCHANGE_COMMODITY_FAIL_HD_FULL = arrayOf<Any?>("Failed to exchange commodity. Your HD is full.", POPUP_ERROR)
        @JvmField val EXCHANGE_COMMODITY_SUCCESS = arrayOf<Any?>("Converted _0_ of commodity into _1_", POPUP_MESSAGE, COMMOD_TO_FILE_KEY)
        @JvmField val EXCHANGE_COMMODITY_SUCCESS_FROM_FILE = arrayOf<Any?>("Converted _0_ from file _1_ into commodity.", POPUP_MESSAGE, FILE_TO_COMMOD_KEY)

        @JvmField val FILE_CHANGED_SINCE_LAST_SAVE = arrayOf<Any?>("You have edited this file since you last compiled it, please save as new name.", POPUP_ERROR)
        @JvmField val FILE_NOT_FOUND = arrayOf<Any?>("File could not be found on HD.", POPUP_ERROR)
        @JvmField val FILE_SUCCESSFULLY_STOLEN = arrayOf<Any?>("You have successfully stolen _0_.", ATTACK_MESSAGE)
        @JvmField val FILE_SUCCESSFULLY_STOLEN_GAME = arrayOf<Any?>("You have successfully stolen _0_ from _1_.", GAME_MESSAGE)
        @JvmField val FTP_FAIL_WRONG_TARGET = arrayOf<Any?>("Invalid target IP: You can only put or get files that you own.", POPUP_ERROR)
        @JvmField val FTP_FAIL_TOO_MANY_PUT_GET = arrayOf<Any?>("You can only use put or get once per script.", POPUP_ERROR)
        @JvmField val FILE_IO_FAIL_INVALID_TYPE = arrayOf<Any?>("You can only perform file operations on files of the type 'text'.", POPUP_ERROR)
        @JvmField val FILE_IO_FAIL_LINE_NUMBER = arrayOf<Any?>("Line number greater than file size.", POPUP_ERROR)
        @JvmField val FILE_IO_FAIL_FILE_SIZE = arrayOf<Any?>("Maximum file size reached for _0_.", POPUP_ERROR)

        @JvmField val FILE_TAKEN = arrayOf<Any?>("_0_ x_1_ taken from HD.", POPUP_MESSAGE)

        @JvmField val FIREWALL_REPLACED = arrayOf<Any?>("Firewall successfully replaced.\n\n Old one placed on HD as _0_.", POPUP_MESSAGE, FIREWALL_REPLACED_KEY)
        @JvmField val FTP_NOT_FOUND = arrayOf<Any?>("The FTP port you attempted to access was not found.", POPUP_ERROR)
        @JvmField val FTP_FAIL_PASSWORD_INCORRECT = arrayOf<Any?>("The password you provided to connect to this FTP site was incorrect.", POPUP_ERROR)
        @JvmField val FTP_PUT_FAIL_HD_FULL = arrayOf<Any?>("The file could not be uploaded because the target HD was full.", POPUP_ERROR)
        @JvmField val FTP_PUT_FAIL = arrayOf<Any?>("The target FTP was not accessible.", POPUP_ERROR)
        @JvmField val FUNCTION_ONLY_WITH_TRIGGERWATCH = arrayOf<Any?>("This function only works from triggerWatch().", POPUP_ERROR)

        @JvmField val GIVEN_FILE = arrayOf<Any?>("You were given _1_ of the file _0_.", POPUP_MESSAGE)

        @JvmField val HD_FULL = arrayOf<Any?>("File could not be saved HD full.", POPUP_ERROR)
        @JvmField val HEAL_FAIL_OVERHEATED = arrayOf<Any?>("You cannot heal a port when it is overheated.", POPUP_ERROR)
        @JvmField val HEAL_FAIL_LIMIT = arrayOf<Any?>("You can only heal _0_ times per defense.", POPUP_ERROR)
        @JvmField val HEAL_SUCCESS = arrayOf<Any?>("Port _0_ healed for _1_.", POPUP_MESSAGE, HEALING_KEY)
        @JvmField val HEAL_FAIL_WEAKENED = arrayOf<Any?>("Cannot heal port _0_ because it is in a weakened state.", POPUP_ERROR)

        @JvmField val INSTALL_SCRIPT_SUCCESS = arrayOf<Any?>("Script successfully installed.", ATTACK_MESSAGE)
        @JvmField val INSTALL_SCRIPT_SUCCESS_GAME = arrayOf<Any?>("Script successfully installed.", GAME_MESSAGE)
        @JvmField val INSTALL_SCRIPT_FAIL_WRONG_TYPE = arrayOf<Any?>("Install script failed. File was of the wrong type.", ATTACK_MESSAGE)
        @JvmField val INSTALL_SCRIPT_FAIL_WRONG_TYPE_GAME = arrayOf<Any?>("Install script failed. File was of the wrong type.", GAME_MESSAGE)
        @JvmField val INSTALL_SCRIPT_FAIL_NO_FILE = arrayOf<Any?>("Install script failed. No file selected.", ATTACK_MESSAGE)
        @JvmField val INSTALL_SCRIPT_FAIL_NO_FILE_GAME = arrayOf<Any?>("Install script failed. No file selected.", GAME_MESSAGE)
        @JvmField val INSTALL_SCRIPT_FAIL_FIREWALL = arrayOf<Any?>("Install script failed due to opponents firewall.", POPUP_ERROR)
        @JvmField val INSTALL_FIREWALL_FAIL_LEVEL = arrayOf<Any?>("You must be firewall level _0_ to install that firewall.", POPUP_ERROR)

        @JvmField val MAX_PROGRAMS_REACHED = arrayOf<Any?>("You can not have that many applications installed. Buy better memory.", POPUP_ERROR)
        @JvmField val MAX_WATCHES_REACHED = arrayOf<Any?>("You can only have 21 watches in the handler at one time.", POPUP_ERROR)
        @JvmField val MESSAGE_FAIL_INVALID_TARGET = arrayOf<Any?>("Invalid message target.", POPUP_ERROR)
        @JvmField val MESSAGE_FAIL_TOO_LONG = arrayOf<Any?>("The message you attempted to send was too long.", POPUP_ERROR)

        @JvmField val OVERHEATED_OPPONENT = arrayOf<Any?>("You have overheated _0_.", ATTACK_MESSAGE)
        @JvmField val OVERHEATED_OPPONENT_GAME = arrayOf<Any?>("You have overheated _0_.", GAME_MESSAGE)

        @JvmField val PLAYER_BUSY = arrayOf<Any?>("Player _0_ has been very busy.", GAME_MESSAGE)
        @JvmField val PORT_NOT_ON = arrayOf<Any?>("Port _0_ at _1_ was not on.", POPUP_ERROR)
        @JvmField val PORT_WAS_NOT_FTP = arrayOf<Any?>("Port _0_ at _1_ was not an FTP port.", POPUP_ERROR)
        @JvmField val PORT_WAS_DUMMY = arrayOf<Any?>("Port _0_ at _1_ was a dummy port.", ATTACK_MESSAGE)
        @JvmField val PORT_ALREADY_ATTACKING = arrayOf<Any?>("Port _0_ is already performing an attack.", ATTACK_MESSAGE)
        @JvmField val PORT_WAS_NOT_WEAKENED = arrayOf<Any?>("Port _0_ at _1_ was not sufficiently weakened.", ATTACK_MESSAGE)
        @JvmField val PORT_ALREADY_UNDER_ATTACK = arrayOf<Any?>("Port _0_ at _1_ was already under attack by _2_.", GAME_MESSAGE)
        @JvmField val PORT_IS_OVERHEATED = arrayOf<Any?>("Port _0_ at _1_ is overheated or frozen.", GAME_MESSAGE)
        @JvmField val PORT_OFF_FAIL_UNDER_ATTACK: Array<out Any> = arrayOf("You can not turn off a port when it is under attack.", POPUP_ERROR)
        @JvmField val PORT_OFF_FAIL_ATTACKING: Array<out Any> = arrayOf("You can not turn off a port when it is attacking.", POPUP_ERROR)
        @JvmField val PORT_OFF_FAIL_OVERHEATED: Array<out Any> = arrayOf("You can not turn off a port when it is overheated.", POPUP_ERROR)
        @JvmField val PORT_OFF_FAIL_DAMAGED: Array<out Any> = arrayOf("You can not turn off a port when it is damaged.", POPUP_ERROR)
        @JvmField val PORT_ON_FAIL_EXCEED_CPU: Array<out Any> = arrayOf("You can not turn on this port because it will exceed your maximum CPU load.", POPUP_ERROR)
        @JvmField val PURCHASE_FAIL_HD_FULL = arrayOf<Any?>("You cannot buy a file when your HD is full.", POPUP_ERROR)
        @JvmField val PURCHASE_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("You did not have enough money in your petty cash to make this purchase.", POPUP_ERROR)
        @JvmField val PURCHASE_FAIL_NOT_HIGH_ENOUGH_LEVEL = arrayOf<Any?>("You are not high enough level to purchase this file.", POPUP_ERROR)
        @JvmField val PURCHASE_NEW_CPU = arrayOf<Any?>("You purchased a new _0_ CPU for your computer. It is installed.", POPUP_MESSAGE)
        @JvmField val PURCHASE_FAIL_OLDER_CPU = arrayOf<Any?>("You start to install your CPU and realize you already have a better one.", POPUP_ERROR)
        @JvmField val PURCHASE_NEW_HD = arrayOf<Any?>("You purchased a new _0_ Hard Drive for your computer. It is installed.", POPUP_MESSAGE)
        @JvmField val PURCHASE_FAIL_OLDER_HD = arrayOf<Any?>("You start to install your new Hard Drive and realize you already have a better one.", POPUP_ERROR)
        @JvmField val PURCHASE_NEW_MEMORY = arrayOf<Any?>("You purchased new _0_ Memory for your computer. It is installed.", POPUP_MESSAGE)
        @JvmField val PURCHASE_FAIL_OLDER_MEMORY = arrayOf<Any?>("You start to install your new Memory and realize you already have a better one.", POPUP_ERROR)
        @JvmField val PURCHASE_SUCCESS = arrayOf<Any?>("You have successfully purchased _0_ _1_(s) for _2_.", POPUP_MESSAGE, FILE_PURCHASED_KEY)
        @JvmField val PURCHASE_FAIL_FILE_NOT_FOUND = arrayOf<Any?>("You attempted to purchase a file that could not be found.", POPUP_ERROR)

        @JvmField val QUEST_COMPLETED = arrayOf<Any?>("Completed Quest: _0_", POPUP_MESSAGE)
        @JvmField val QUEST_GIVEN = arrayOf<Any?>("Given Quest: _0_", POPUP_MESSAGE)

        @JvmField val RECEIVED_COMMODITY = arrayOf<Any?>("Received _0_ _1_.", REDIRECT_MESSAGE)
        @JvmField val RECEIVED_COMMODITY_GAME = arrayOf<Any?>("Received _0_ _1_ from _2_.", GAME_MESSAGE)
        @JvmField val RECEIVED_COMMODITY_FAIL = arrayOf<Any?>("Transaction failed, make sure you have an active redirect port.", POPUP_ERROR)
        @JvmField val REDIRECT_FAIL_WRONG_TYPE = arrayOf<Any?>("You cannot redirect shipments from a port that is not a redirect port.", REDIRECT_MESSAGE)
        @JvmField val REDIRECT_XP_MAX = arrayOf<Any?>("You can not gain anymore redirect experience off _0_ at this time.", REDIRECT_MESSAGE)
        @JvmField val REDIRECT_EXCEEDED_MAXIMUM_TIMEOUT = arrayOf<Any?>("Your redirect application reached its maximum timeout for redirecting off one port.", REDIRECT_MESSAGE)
        @JvmField val REDIRECT_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("You must have \$10 in your petty cash to start a redirect.", POPUP_ERROR)
        @JvmField val REDIRECT_FAIL_ALREADY_REDIRECTING = arrayOf<Any?>("This port is already redirecting shipments.", REDIRECT_MESSAGE)
        @JvmField val REDIRECT_FAIL_OVERHEATED = arrayOf<Any?>("Can't redirect when port is overheated.", REDIRECT_MESSAGE)
        @JvmField val REPAIR_FAIL_LEVEL = arrayOf<Any?>("You need to have level _1_ to repair using _0_.", POPUP_ERROR)
        @JvmField val REMOVE_FIREWALL_SUCCESS = arrayOf<Any?>("Firewall successfully removed.\n\nSaved to HD as _0_.", POPUP_MESSAGE, FIREWALL_REMOVED_KEY)
        @JvmField val REMOVE_FIREWALL_FAIL_HD_FULL = arrayOf<Any?>("You can't remove the firewall while your HD is full.", POPUP_ERROR)
        @JvmField val REPAIR_FAIL_NOT_ENOUGH_COMMODITIES = arrayOf<Any?>("You don't have enough commodities to repair _0_.\n\nRequired: _1_", POPUP_ERROR)
        @JvmField val REPAIR_SUCCESS = arrayOf<Any?>("Card repaired: _0_.\n\nUsed _1_", POPUP_MESSAGE, CARD_REPAIRED_KEY)
        @JvmField val REPLACE_APPLICATION_UNDER_ATTACK = arrayOf<Any?>("You cannot replace an application while a port is under attack.", POPUP_ERROR)
        @JvmField val REPLACE_APPLICATION_ATTACKING = arrayOf<Any?>("You cannot replace an application while a port is attacking.", POPUP_ERROR)
        @JvmField val REPLACE_APPLICATION_OVERHEATED = arrayOf<Any?>("You cannot replace an application while a port is overheated.", POPUP_ERROR)
        @JvmField val REPLACE_APPLICATION_SUCCESS = arrayOf<Any?>("Application successfully replaced.", POPUP_MESSAGE, APP_REPLACED_KEY)
        @JvmField val REDIRECT_FINISHED_GAME = arrayOf<Any?>("Port _0_ finished redirecting.", GAME_MESSAGE)
        @JvmField val REDIRECT_FINISHED = arrayOf<Any?>("Finished redirecting.", REDIRECT_MESSAGE)

        @JvmField val SAVE_FAIL_HD_FULL = arrayOf<Any?>("Folder could not be created, is your HD full?", POPUP_ERROR)
        @JvmField val SCAN_FAIL_NO_MONEY = arrayOf<Any?>("You do not have enough money in your petty cash to scan.", POPUP_ERROR)
        @JvmField val SCAN_FAIL_OVERHEATED = arrayOf<Any?>("Cannot scan when your computer is overheated.", POPUP_ERROR)
        @JvmField val SECRET_DOCUMENT_TASK_COMPLETED = arrayOf<Any?>("You have finished one of the tasks assigned in a secret document.", GAME_MESSAGE)
        @JvmField val SELL_FAIL_WRONG_TYPE = arrayOf<Any?>("You cannot sell this type of file back to the store.", POPUP_ERROR)
        @JvmField val SELL_FAIL_BANK_PORT = arrayOf<Any?>("Could not sell file(s): you must have a non-dummy bank port turned on.", POPUP_ERROR)
        @JvmField val STEAL_FILE_FAIL_WRONG_TYPE = arrayOf<Any?>("You can only steal a file on an FTP port.", ATTACK_MESSAGE)

        @JvmField val TRANSFER_FAIL_NOOB_LEVEL = arrayOf<Any?>("Transaction failed, you can not receive transfers until you are level _0_ or higher.", POPUP_ERROR)
        @JvmField val TRANSFER_RECEIVED = arrayOf<Any?>("Received transfer of \$_0_ from _1_.", POPUP_MESSAGE, TRANSFER_FROM_KEY)
        @JvmField val TRANSFER_RECEIVE_FAIL_BANK_PORT = arrayOf<Any?>("Could not recieve transfer of \$_0_ from _1_.  You must have a non-dummy bank port turned on.", POPUP_ERROR)
        @JvmField val TRANSFER_SEND_FAIL_BANK_PORT = arrayOf<Any?>("Could not complete the transfer: target computer does not have an non-dummy bank port turned on.", POPUP_ERROR)
        @JvmField val TRANSFER_SENT_SUCCESSFUL = arrayOf<Any?>("Transfer of \$_0_ successful.", POPUP_MESSAGE, TRANSFER_TO_KEY)
        @JvmField val TRANSFER_FAIL_NOOB = arrayOf<Any?>("You cannot transfer money until you are total level 15 or higher.", POPUP_ERROR)

        @JvmField val UNINSTALL_PORT_FAIL_UNDER_ATTACK: Array<out Any> = arrayOf("You cannot uninstall an application while a port is under attack.", POPUP_ERROR)
        @JvmField val UNINSTALL_PORT_FAIL_ATTACKING: Array<out Any> = arrayOf("You cannot uninstall an application while a port is attacking.", POPUP_ERROR)
        @JvmField val UNINSTALL_PORT_FAIL_OVERHEATED: Array<out Any> = arrayOf("You cannot uninstall an application while a port is overheated.", POPUP_ERROR)
        @JvmField val UNEQUIP_FAIL_HD_FULL = arrayOf<Any?>("Could not uninstall card: Unequipping this card would put you over your maximum file limit.", POPUP_ERROR)
        @JvmField val UNEQUIP_FAIL_CPU_RESTRICTIONS = arrayOf<Any?>("Could not uninstall card: Unequipping this card would put you over your CPU limit and overheat you.", POPUP_ERROR)
        @JvmField val UNEQUIP_FAIL_WATCH_RESTRICTIONS = arrayOf<Any?>("Could not uninstall card: Unequipping this card would put you over your maximum number of watches.", POPUP_ERROR)

        @JvmField val UPGRADE_FILE_SIZE = arrayOf<Any?>("If you upgraded your account the maximum file size is " + (Computer.PAY_FILE_SIZE_LIMIT / Computer.FREE_FILE_SIZE_LIMIT) + " times as much", POPUP_MESSAGE)

        @JvmField val VOTE_FAIL_NOOB_LEVEL = arrayOf<Any?>("You must be level _0_ or greater to vote for sites.", POPUP_ERROR)
        @JvmField val VOTE_FAIL_OWN_SITE = arrayOf<Any?>("You cannot vote for your own web site.", POPUP_ERROR)
        @JvmField val VOTE_SUCCESS = arrayOf<Any?>("Vote successful _0_ left.", POPUP_MESSAGE, VOTE_SUCCESSFUL_KEY)
        @JvmField val VOTE_FAIL_NO_VOTES = arrayOf<Any?>("You do not currently have any votes available.", POPUP_ERROR)
        @JvmField val VOTE_FAIL_HTTP_NOT_ON = arrayOf<Any?>("You cannot receive a vote if your HTTP is not on.", POPUP_ERROR)

        @JvmField val WEBSITE_SAVE_FAIL_TOO_BIG = arrayOf<Any?>("Your web-page cannot exceed 30,000 characters in size.", POPUP_ERROR)
        @JvmField val WATCH_ON_FAIL = arrayOf<Any?>("You can not have this many watches running at once, buy some better memory.", POPUP_ERROR)

        @JvmField val ZOMBIE_OVERHEATED = arrayOf<Any?>("Computer at _0_ just overheated!", ATTACK_MESSAGE)
        @JvmField val ZOMBIE_ATTEMPT_FAIL = arrayOf<Any?>("You cannot access this port.", ATTACK_MESSAGE)
        @JvmField val ZOMBIE_FAIL_NOT_ENOUGH_MONEY = arrayOf<Any?>("It costs \$20 to attempt an attack from a zombie port.", POPUP_ERROR)
    }
}
