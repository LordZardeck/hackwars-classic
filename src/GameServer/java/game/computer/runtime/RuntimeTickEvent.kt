package game.computer.runtime

import game.ApplicationCommand
import game.ApplicationPayload

data class RuntimeLogMessagePayload(
    val message: String,
    val ip: String,
    val timestamp: Long,
) : ApplicationPayload {
    override fun getCommand(): ApplicationCommand = ApplicationCommand.of("logmessage")
}

data class RuntimeApplicationDataDispatch(
    val command: ApplicationCommand,
    val payload: ApplicationPayload,
    val port: Int = 0,
    val sourceIp: String = "",
    val sourcePort: Int = 0,
    val source: Int = 0,
) {
    constructor(
        payload: ApplicationPayload,
        port: Int = 0,
        sourceIp: String = "",
        sourcePort: Int = 0,
        source: Int = 0,
    ) : this(payload.getCommand(), payload, port, sourceIp, sourcePort, source)
}

sealed interface RuntimeTickEvent {
    data class PersistRequested(val autoSave: Boolean = false) : RuntimeTickEvent
    data object UnloadRequested : RuntimeTickEvent
    data object PlayerCountDecrementRequested : RuntimeTickEvent
    data class ApplicationDataDispatchRequested(
        val applicationData: RuntimeApplicationDataDispatch,
        val targetIp: String,
    ) : RuntimeTickEvent
    data class LogEntry(val message: String, val ip: String, val timestamp: Long) : RuntimeTickEvent
    data class PlaySessionRecorded(val ip: String, val startedAt: Long, val endedAt: Long) : RuntimeTickEvent
    data class DailyPayIssued(val amount: Float, val targetIp: String) : RuntimeTickEvent
    data class HttpXpIssued(val amount: Float, val targetIp: String) : RuntimeTickEvent
    data class BankMoneyAdded(val amount: Float) : RuntimeTickEvent
    data class PettyCashAdded(val amount: Float) : RuntimeTickEvent
    data class AttackContinueRequested(val portNumber: Int, val targetPort: Int) : RuntimeTickEvent
    data class OverheatAnnounced(val ip: String) : RuntimeTickEvent
    data class OpponentOverheated(val targetIp: String, val windowHandle: Int, val accessing: String) : RuntimeTickEvent
    data class ZombieOverheated(val targetIp: String, val maliciousIp: String) : RuntimeTickEvent
    data class CaptchaRequested(val payload: RuntimeCaptchaPayload) : RuntimeTickEvent
    data object GrantFilesRequested : RuntimeTickEvent
    data object EquipmentRefreshRequested : RuntimeTickEvent
}
