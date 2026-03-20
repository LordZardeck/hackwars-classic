package game.payload

import game.ApplicationCommand
import game.ApplicationPayload
import game.HackerFile

data class SetPreferencesPayload(
    val preferences: HashMap<Any?, Any?>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("setpreferences")
    fun legacyParameters(): Any = arrayOf<Any?>(null, preferences)
}

data class MessageTextPayload(
    val text: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("message")
    fun legacyParameters(): Any = text
}

@Suppress("ArrayInDataClass")
data class StructuredMessagePayload(
    val message: Array<Any?>,
    val parameters: Array<Any?>? = null,
    val portInfo: Array<Any?>? = null
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("message")

    fun legacyParameters(): Any = when {
        parameters == null && portInfo == null -> message
        portInfo == null -> arrayOf<Any?>(message, parameters)
        else -> arrayOf<Any?>(message, parameters, portInfo)
    }
}

data class SendEmailPayload(
    val message: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("sendemail")
    fun legacyParameters(): Any = message
}

data class SendFacebookPayload(
    val message: String,
    val targetIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("sendfacebook")
    fun legacyParameters(): Any = arrayOf<Any?>(message, targetIp)
}

data class DailyPaySetPayload(
    val bountyIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("dailypayset")
    fun legacyParameters(): Any = bountyIp
}

data class PettyCashTransferPayload(
    val amount: Float,
    val returnAmount: Float = 0.0f,
    val sendMessage: Boolean = true
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("pettycash")
    fun legacyParameters(): Any = when {
        returnAmount == 0.0f && sendMessage -> amount
        sendMessage -> arrayOf<Any?>(amount, returnAmount)
        else -> arrayOf<Any?>(amount, false)
    }
}

data class TransferPayload(
    val targetIp: String,
    val amount: Float
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("transfer")
    fun legacyParameters(): Any = arrayOf<Any?>(targetIp, amount)
}

data class CommodityPayload(
    val commodity: Int,
    val value: Float,
    val redirectPort: Int,
    val targetIp: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("commodity")
    fun legacyParameters(): Any = arrayOf<Any?>(commodity, value, redirectPort, targetIp)
}

data class RequestPurchasePayload(
    val fileName: String,
    val quantity: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("requestpurchase")
    fun legacyParameters(): Any = arrayOf<Any?>(fileName, quantity)
}

data class ContinuePurchasePayload(
    val file: HackerFile,
    val revenueTarget: String,
    val sellerType: Int
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("continuepurchase")
    fun legacyParameters(): Any = arrayOf<Any?>(file, revenueTarget, sellerType)
}

data class RequestWebPagePayload(
    val requestParameters: HashMap<Any?, Any?>?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("requestwebpage")
    fun legacyParameters(): Any? = requestParameters
}

data class QuestInformationPayload(
    val questParameters: HashMap<Any?, Any?>?,
    val quests: ArrayList<Any?>
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("questinformation")
    fun legacyParameters(): Any = arrayOf<Any?>(questParameters, quests)
}

data class WebPagePayload(
    val title: String,
    val body: String,
    val files: Array<Any?>?,
    val packetId: Int?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("webpage")
    fun legacyParameters(): Any = arrayOf<Any?>(title, body, files, packetId)
}

data class SavePagePayload(
    val title: String,
    val body: String
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("savepage")
    fun legacyParameters(): Any = arrayOf<Any?>(title, body)
}

data class SubmitPayload(
    val submitParameters: HashMap<Any?, Any?>?
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("submit")
    fun legacyParameters(): Any? = submitParameters
}
