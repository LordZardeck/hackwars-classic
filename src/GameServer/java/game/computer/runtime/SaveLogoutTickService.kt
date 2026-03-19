package game.computer.runtime

class SaveLogoutTickService {
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
                    if (queued.function == "pettycash" || queued.function == "requestwebpage") {
                        events += RuntimeTickEvent.DeferredTaskRetried(queued.function, queued.sourceIp)
                    }
                }
            }

            if (!state.loadFailure) {
                events += RuntimeTickEvent.PersistRequested
            }

            events += RuntimeTickEvent.UnloadRequested
            events += RuntimeTickEvent.PlayerCountDecrementRequested
            state.loaded = false
            return events
        }

        if (!state.loadFailure) {
            if (state.lastSave == 0L) {
                state.lastSave = state.now
            }
            if (state.now - state.lastSave > state.autoSaveMs) {
                events += RuntimeTickEvent.PersistRequested
                state.lastSave = state.now
            }
        }

        return events
    }
}
