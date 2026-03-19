package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLogoutTickServiceTest {
    private val service = SaveLogoutTickService()

    @Test
    fun tick_emitsUnloadAndPersistWhenTimeoutTriggers() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 0
        state.computerTimeoutMs = 10_000L

        val events = service.tick(state)

        assertTrue(events.contains(RuntimeTickEvent.PersistRequested))
        assertTrue(events.contains(RuntimeTickEvent.UnloadRequested))
        assertTrue(events.contains(RuntimeTickEvent.PlayerCountDecrementRequested))
        assertFalse(state.loaded)
    }

    @Test
    fun tick_autosavesWhenSaveWindowExpires() {
        val state = baseRuntimeState(now = 2_000L)
        state.loaded = true
        state.lastAccessed = 0
        state.lastSave = 500L
        state.autoSaveMs = 1_000L
        state.loadFailure = false

        val events = service.tick(state)

        assertEquals(listOf(RuntimeTickEvent.PersistRequested), events)
        assertEquals(2_000L, state.lastSave)
    }
}
