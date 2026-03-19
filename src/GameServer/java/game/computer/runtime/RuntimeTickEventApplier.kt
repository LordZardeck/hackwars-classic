package game.computer.runtime

object RuntimeTickEventApplier {
    fun apply(events: Iterable<RuntimeTickEvent>, sink: RuntimeTickEventSink) {
        events.forEach { event ->
            when (event) {
                is RuntimeTickEvent.PersistRequested -> sink.persistRequested(event.autoSave)
                RuntimeTickEvent.UnloadRequested -> sink.unloadRequested()
                RuntimeTickEvent.PlayerCountDecrementRequested -> sink.playerCountDecrementRequested()
                is RuntimeTickEvent.ApplicationDataDispatchRequested ->
                    sink.applicationDataDispatchRequested(event.applicationData, event.targetIp)
                is RuntimeTickEvent.LogEntry -> sink.logEntry(event.message, event.ip, event.timestamp)
                is RuntimeTickEvent.PlaySessionRecorded -> sink.playSessionRecorded(event.ip, event.startedAt, event.endedAt)
                is RuntimeTickEvent.DailyPayIssued -> sink.dailyPayIssued(event.amount, event.targetIp)
                is RuntimeTickEvent.HttpXpIssued -> sink.httpXpIssued(event.amount, event.targetIp)
                is RuntimeTickEvent.BankMoneyAdded -> sink.bankMoneyAdded(event.amount)
                is RuntimeTickEvent.PettyCashAdded -> sink.pettyCashAdded(event.amount)
                is RuntimeTickEvent.AttackContinueRequested -> sink.attackContinueRequested(event.portNumber, event.targetPort)
                is RuntimeTickEvent.OverheatAnnounced -> sink.overheatAnnounced(event.ip)
                is RuntimeTickEvent.OpponentOverheated -> sink.opponentOverheated(event.targetIp, event.windowHandle, event.accessing)
                is RuntimeTickEvent.ZombieOverheated -> sink.zombieOverheated(event.targetIp, event.maliciousIp)
                is RuntimeTickEvent.CaptchaRequested -> sink.captchaRequested(event.payload)
                RuntimeTickEvent.GrantFilesRequested -> sink.grantFilesRequested()
                RuntimeTickEvent.EquipmentRefreshRequested -> sink.equipmentRefreshRequested()
            }
        }
    }
}
