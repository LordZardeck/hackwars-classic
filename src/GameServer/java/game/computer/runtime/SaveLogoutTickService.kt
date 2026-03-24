package game.computer.runtime

import game.payload.MessageTextPayload
import game.payload.WebPagePayload

class SaveLogoutTickService {
    companion object {
        internal const val LOAD_FAILURE_WEBPAGE_TITLE = "Server Not Found"
        internal const val LOAD_FAILURE_WEBPAGE_BODY =
            "<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>"
    }

    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()
        val expired = (
                state.now - state.lastAccessed > state.computerTimeoutMs ||
                        state.logoutRequested ||
                        state.loadFailure ||
                        (state.countDown && state.now - state.countDownStart > state.countDownLengthMs)
                ) && state.loaded

        if (expired) {
            if (state.loadFailure && state.loadRequester.isNotBlank() && state.loadRequester != state.ip) {
                state.pendingTasks.firstOrNull()?.let { queued ->
                    when (queued.command) {
                        com.hackwars.rpc.GameCommands.PETTYCASH.command -> {
                            events += RuntimeTickEvent.ApplicationDataDispatchRequested(
                                applicationData = RuntimeApplicationDataDispatch(
                                    payload = queued.payload,
                                    port = queued.port,
                                    sourceIp = queued.sourceIp,
                                    sourcePort = queued.sourcePort,
                                    source = queued.source,
                                ),
                                targetIp = queued.sourceIp,
                            )
                        }

                        com.hackwars.rpc.GameCommands.REQUESTWEBPAGE.command -> {
                            events += RuntimeTickEvent.ApplicationDataDispatchRequested(
                                applicationData = RuntimeApplicationDataDispatch(
                                    payload = WebPagePayload(
                                        LOAD_FAILURE_WEBPAGE_TITLE,
                                        LOAD_FAILURE_WEBPAGE_BODY,
                                        null,
                                        0,
                                    ),
                                    sourceIp = state.ip,
                                ),
                                targetIp = queued.sourceIp,
                            )
                        }
                    }
                    state.pendingTasks.removeAt(0)
                }
                events += RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        payload = MessageTextPayload(state.errorMessage),
                        sourceIp = state.ip,
                    ),
                    targetIp = state.loadRequester,
                )
            }

            if (!state.loadFailure) {
                events += RuntimeTickEvent.PersistRequested(autoSave = false)
            }

            events += RuntimeTickEvent.UnloadRequested
            if (state.type != 1) {
                events += RuntimeTickEvent.PlayerCountDecrementRequested
            }
            state.loaded = false
            return events
        }

        if (!state.loadFailure) {
            if (state.lastSave == 0L) {
                state.lastSave = state.now
            }
            if (state.now - state.lastSave > state.autoSaveMs) {
                events += RuntimeTickEvent.PersistRequested(autoSave = true)
                state.lastSave = state.now
            }
        }

        return events
    }
}
