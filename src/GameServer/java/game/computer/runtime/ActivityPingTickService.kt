package game.computer.runtime

class ActivityPingTickService {
    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()
        if (!state.loggedIn) {
            return events
        }

        if (state.lastPingTime != 0L && state.now - state.lastPingTime > state.pingTimeoutMs) {
            events += RuntimeTickEvent.PlaySessionRecorded(state.ip, state.logInTime, state.lastPingTime)
            state.lastPingTime = 0
            state.logInTime = 0
        }

        if (
            state.lastClientPacketTime != 0L &&
            state.logInTime != 0L &&
            state.lastClientPacketTime != state.logInTime &&
            state.now - state.lastClientPacketTime > state.clientPacketTimeoutMs
        ) {
            events += RuntimeTickEvent.PlaySessionRecorded(state.ip, state.logInTime, state.lastClientPacketTime)
            state.lastClientPacketTime = 0
            state.logInTime = 0
        }

        return events
    }
}
