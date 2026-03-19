package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPingTickServiceTest {
    private val service = ActivityPingTickService()

    @Test
    fun tick_recordsPingSessionWhenPingTimesOut() {
        val state = baseRuntimeState(now = 3_000L)
        state.lastPingTime = 1_000L
        state.logInTime = 500L

        val events = service.tick(state)

        assertEquals(
            listOf(RuntimeTickEvent.PlaySessionRecorded(state.ip, 500L, 1_000L)),
            events
        )
        assertEquals(0L, state.lastPingTime)
        assertEquals(0L, state.logInTime)
    }

    @Test
    fun tick_recordsPacketSessionWhenClientPacketsStop() {
        val state = baseRuntimeState(now = 3_000L)
        state.lastClientPacketTime = 1_200L
        state.logInTime = 500L

        val events = service.tick(state)

        assertEquals(
            listOf(RuntimeTickEvent.PlaySessionRecorded(state.ip, 500L, 1_200L)),
            events
        )
        assertEquals(0L, state.lastClientPacketTime)
        assertEquals(0L, state.logInTime)
    }
}
