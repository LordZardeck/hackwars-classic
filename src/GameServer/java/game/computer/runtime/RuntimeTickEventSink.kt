package game.computer.runtime

interface RuntimeTickEventSink {
    fun persistRequested(autoSave: Boolean)
    fun unloadRequested()
    fun playerCountDecrementRequested()
    fun applicationDataDispatchRequested(applicationData: RuntimeApplicationDataDispatch, targetIp: String)
    fun logEntry(message: String, ip: String, timestamp: Long)
    fun playSessionRecorded(ip: String, startedAt: Long, endedAt: Long)
    fun dailyPayIssued(amount: Float, targetIp: String)
    fun httpXpIssued(amount: Float, targetIp: String)
    fun bankMoneyAdded(amount: Float)
    fun pettyCashAdded(amount: Float)
    fun attackContinueRequested(portNumber: Int, targetPort: Int)
    fun overheatAnnounced(ip: String)
    fun opponentOverheated(targetIp: String, windowHandle: Int, accessing: String)
    fun zombieOverheated(targetIp: String, maliciousIp: String)
    fun captchaRequested(payload: RuntimeCaptchaPayload)
    fun grantFilesRequested()
    fun equipmentRefreshRequested()
}
