package game.computer.runtime

import game.payload.MessageTextPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeTickEventApplierTest {
    @Test
    fun apply_dispatchesEventsInOrder() {
        val sink = RecordingRuntimeTickEventSink()
        RuntimeTickEventApplier.apply(
            listOf<RuntimeTickEvent>(
                RuntimeTickEvent.PersistRequested(autoSave = true),
                RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        payload = MessageTextPayload("boom"),
                        port = 4,
                        sourceIp = "1.1.1.1",
                        sourcePort = 9,
                        source = 1,
                    ),
                    targetIp = "9.9.9.9",
                ),
                RuntimeTickEvent.LogEntry("one", "1.1.1.1", 10L),
                RuntimeTickEvent.DailyPayIssued(5f, "2.2.2.2"),
                RuntimeTickEvent.EquipmentRefreshRequested,
            ),
            sink,
        )

        assertEquals(
            listOf(
                "persist:true",
                "dispatch:message:9.9.9.9:4:1.1.1.1:9:1",
                "log:one",
                "pay:5.0",
                "refresh",
            ),
            sink.calls,
        )
    }
}

private class RecordingRuntimeTickEventSink : RuntimeTickEventSink {
    val calls = mutableListOf<String>()

    override fun persistRequested(autoSave: Boolean) {
        calls += "persist:$autoSave"
    }

    override fun unloadRequested() {
        calls += "unload"
    }

    override fun playerCountDecrementRequested() {
        calls += "decrement"
    }

    override fun applicationDataDispatchRequested(applicationData: RuntimeApplicationDataDispatch, targetIp: String) {
        calls += "dispatch:${applicationData.command.wireName()}:$targetIp:${applicationData.port}:${applicationData.sourceIp}:${applicationData.sourcePort}:${applicationData.source}"
    }

    override fun logEntry(message: String, ip: String, timestamp: Long) {
        calls += "log:$message"
    }

    override fun playSessionRecorded(ip: String, startedAt: Long, endedAt: Long) {
        calls += "session:$ip"
    }

    override fun dailyPayIssued(amount: Float, targetIp: String) {
        calls += "pay:$amount"
    }

    override fun httpXpIssued(amount: Float, targetIp: String) {
        calls += "http:$amount"
    }

    override fun bankMoneyAdded(amount: Float) {
        calls += "bank:$amount"
    }

    override fun pettyCashAdded(amount: Float) {
        calls += "petty:$amount"
    }

    override fun attackContinueRequested(portNumber: Int, targetPort: Int) {
        calls += "attack:$portNumber:$targetPort"
    }

    override fun overheatAnnounced(ip: String) {
        calls += "overheat:$ip"
    }

    override fun opponentOverheated(targetIp: String, windowHandle: Int, accessing: String) {
        calls += "opponent:$targetIp:$windowHandle:$accessing"
    }

    override fun zombieOverheated(targetIp: String, maliciousIp: String) {
        calls += "zombie:$targetIp:$maliciousIp"
    }

    override fun captchaRequested(payload: RuntimeCaptchaPayload) {
        calls += "captcha:${payload.unlockKey}"
    }

    override fun grantFilesRequested() {
        calls += "grant"
    }

    override fun equipmentRefreshRequested() {
        calls += "refresh"
    }
}
