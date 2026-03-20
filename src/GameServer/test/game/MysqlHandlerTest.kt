package game

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MysqlHandlerTest {
    @Test
    fun saveCoordinator_processesOnlyTheLatestSaveForAnIp() = runTest {
        MysqlHandler.SAVE_COUNTER = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val processed = mutableListOf<Array<Any?>>()
        val processor = object : CheckOutHandler() {
            override fun processWork(o: Array<Any?>) {
                processed += o
            }
        }
        val coordinator = SaveCoordinator(
            runtime = server.runtime.GameServerRuntime(dispatcher),
            workerDispatcher = dispatcher,
            workerCount = 1,
            workerFactory = { processor }
        )

        coordinator.start()
        coordinator.enqueue(arrayOf("10.0.0.1", mockComputer(), "token", false, "Title", "Body"))
        coordinator.enqueue(arrayOf("10.0.0.1", mockComputer(), "token", false, "Title 2", "Body 2"))

        advanceUntilIdle()

        assertEquals(1, processed.size)
        assertEquals("Title 2", processed[0][4])
        assertEquals(0, MysqlHandler.SAVE_COUNTER)

        coordinator.shutdown()
        coordinator.join()
    }

    @Test
    fun saveCoordinator_startsTenWorkersByDefault() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val createdWorkers = AtomicInteger(0)
        val coordinator = SaveCoordinator(
            runtime = server.runtime.GameServerRuntime(dispatcher),
            workerDispatcher = dispatcher,
            workerCount = 10,
            workerFactory = {
                createdWorkers.incrementAndGet()
                object : CheckOutHandler() {}
            }
        )

        coordinator.start()
        advanceUntilIdle()

        assertEquals(10, createdWorkers.get())

        coordinator.shutdown()
        coordinator.join()
    }

    private fun mockComputer(): Computer {
        return org.mockito.kotlin.mock()
    }
}
