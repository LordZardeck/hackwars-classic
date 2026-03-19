package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeTickEventApplierTest {
    @Test
    fun apply_dispatchesEventsInOrder() {
        val sink = RecordingRuntimeTickEventSink()
        RuntimeTickEventApplier.apply(
            listOf(
                RuntimeTickEvent.PersistRequested,
                RuntimeTickEvent.LogEntry("one", "1.1.1.1", 10L),
                RuntimeTickEvent.DailyPayIssued(5f, "2.2.2.2"),
                RuntimeTickEvent.EquipmentRefreshRequested
            ),
            sink
        )

        assertEquals(
            listOf(
                "persist",
                "log:one",
                "pay:5.0",
                "refresh"
            ),
            sink.calls
        )
    }
}

private class RecordingRuntimeTickEventSink : RuntimeTickEventSink {
    val calls = mutableListOf<String>()

    override fun persistRequested() {
        calls += "persist"
    }

    override fun unloadRequested() {
        calls += "unload"
    }

    override fun playerCountDecrementRequested() {
        calls += "decrement"
    }

    override fun deferredTaskRetried(function: String, sourceIp: String) {
        calls += "retry:$function:$sourceIp"
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
