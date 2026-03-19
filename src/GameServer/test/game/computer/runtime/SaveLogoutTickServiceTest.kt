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

        assertTrue(events.contains(RuntimeTickEvent.PersistRequested(autoSave = false)))
        assertTrue(events.contains(RuntimeTickEvent.UnloadRequested))
        assertTrue(events.contains(RuntimeTickEvent.PlayerCountDecrementRequested))
        assertFalse(state.loaded)
    }

    @Test
    fun tick_timeoutForNpc_skipsPlayerCountDecrement() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 0
        state.computerTimeoutMs = 10_000L
        state.type = 1

        val events = service.tick(state)

        assertTrue(events.contains(RuntimeTickEvent.PersistRequested(autoSave = false)))
        assertTrue(events.contains(RuntimeTickEvent.UnloadRequested))
        assertFalse(events.contains(RuntimeTickEvent.PlayerCountDecrementRequested))
        assertFalse(state.loaded)
    }

    @Test
    fun tick_loadFailureRetriesFirstPendingPettyCash_consumesIt_andSkipsPersist() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 0
        state.computerTimeoutMs = 10_000L
        state.loadFailure = true
        state.loadRequester = "8.8.8.8"
        state.errorMessage = "load failed"
        state.pendingTasks += RuntimeQueuedTask(
            function = "pettycash",
            sourceIp = "3.3.3.3",
            parameters = 42.5f,
            port = 9,
            sourcePort = 12,
            source = 1,
        )
        state.pendingTasks += RuntimeQueuedTask("requestwebpage", "4.4.4.4")

        val events = service.tick(state)

        assertFalse(events.contains(RuntimeTickEvent.PersistRequested(autoSave = false)))
        assertTrue(
            events.contains(
                RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        function = "pettycash",
                        parameters = 42.5f,
                        port = 9,
                        sourceIp = "3.3.3.3",
                        sourcePort = 12,
                        source = 1,
                    ),
                    targetIp = "3.3.3.3",
                )
            )
        )
        assertTrue(
            events.contains(
                RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        function = "message",
                        parameters = "load failed",
                        sourceIp = "5.5.5.5",
                    ),
                    targetIp = "8.8.8.8",
                )
            )
        )
        assertTrue(events.contains(RuntimeTickEvent.UnloadRequested))
        assertEquals(listOf(RuntimeQueuedTask("requestwebpage", "4.4.4.4")), state.pendingTasks)
        assertFalse(state.loaded)
    }

    @Test
    fun tick_loadFailureConsumesUnrelatedFirstTaskWithoutRetrying() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 0
        state.computerTimeoutMs = 10_000L
        state.loadFailure = true
        state.loadRequester = "8.8.8.8"
        state.errorMessage = "load failed"
        state.pendingTasks += RuntimeQueuedTask("message", "3.3.3.3")
        state.pendingTasks += RuntimeQueuedTask("pettycash", "4.4.4.4")

        val events = service.tick(state)

        assertFalse(
            events.any {
                it is RuntimeTickEvent.ApplicationDataDispatchRequested &&
                    it.applicationData.function == "pettycash"
            }
        )
        assertTrue(
            events.contains(
                RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        function = "message",
                        parameters = "load failed",
                        sourceIp = "5.5.5.5",
                    ),
                    targetIp = "8.8.8.8",
                )
            )
        )
        assertEquals(listOf(RuntimeQueuedTask("pettycash", "4.4.4.4")), state.pendingTasks)
    }

    @Test
    fun tick_loadFailureConvertsRequestWebpageIntoLegacy408Response() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastAccessed = 0
        state.computerTimeoutMs = 10_000L
        state.loadFailure = true
        state.loadRequester = "8.8.8.8"
        state.errorMessage = "load failed"
        state.pendingTasks += RuntimeQueuedTask("requestwebpage", "4.4.4.4")

        val events = service.tick(state)
        val webpageDispatch = events.filterIsInstance<RuntimeTickEvent.ApplicationDataDispatchRequested>()
            .first { it.targetIp == "4.4.4.4" }

        assertEquals("webpage", webpageDispatch.applicationData.function)
        assertEquals("5.5.5.5", webpageDispatch.applicationData.sourceIp)
        val payload = webpageDispatch.applicationData.parameters as Array<*>
        assertEquals("Server Not Found", payload[0])
        assertEquals(0, payload[3])
        assertTrue(
            events.contains(
                RuntimeTickEvent.ApplicationDataDispatchRequested(
                    applicationData = RuntimeApplicationDataDispatch(
                        function = "message",
                        parameters = "load failed",
                        sourceIp = "5.5.5.5",
                    ),
                    targetIp = "8.8.8.8",
                )
            )
        )
        assertTrue(state.pendingTasks.isEmpty())
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

        assertEquals(
            listOf(RuntimeTickEvent.PersistRequested(autoSave = true)),
            events
        )
        assertFalse(events.contains(RuntimeTickEvent.UnloadRequested))
        assertFalse(events.contains(RuntimeTickEvent.PlayerCountDecrementRequested))
        assertEquals(2_000L, state.lastSave)
    }

    @Test
    fun tick_loadFailureWithoutPendingTasks_unloadsImmediately() {
        val state = baseRuntimeState(now = 2_000L)
        state.loaded = true
        state.lastAccessed = 1_900L
        state.lastSave = 500L
        state.autoSaveMs = 1_000L
        state.loadFailure = true

        val events = service.tick(state)

        assertEquals(
            listOf(
                RuntimeTickEvent.UnloadRequested,
                RuntimeTickEvent.PlayerCountDecrementRequested,
            ),
            events
        )
        assertEquals(500L, state.lastSave)
        assertFalse(state.loaded)
    }

    @Test
    fun tick_initialSaveTimestampIsSeededBeforeAutosaveStarts() {
        val state = baseRuntimeState(now = 2_000L)
        state.loaded = true
        state.lastAccessed = 1_900L
        state.lastSave = 0L
        state.autoSaveMs = 1_000L

        val events = service.tick(state)

        assertTrue(events.isEmpty())
        assertEquals(2_000L, state.lastSave)
        assertTrue(state.loaded)
    }
}
