package game

import game.payload.RequestWebPagePayload
import game.payload.WebPagePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ComputerHandlerTest {
    @Test
    fun `loading computer queues incoming application data on the computer`() {
        val handler = ComputerHandler(null)
        val computer = Computer("188.230.7.189", handler, -1, null)
        computer.Loading = true
        handler.addComputer(computer)

        handler.addData(ApplicationData(RequestWebPagePayload(HashMap()), 0, "source"), computer.ip)

        val task = pollTask(handler) ?: error("expected a queued handler task")
        processTask(handler, task)

        assertEquals(1, computer.pendingTaskCount())
        assertNull(pollTask(handler))
    }

    @Test
    fun `recent failed remote load is served from cache instead of recreating a computer`() {
        val handler = ComputerHandler(null)
        val ip = "188.230.7.189"
        val requesterIp = "900.800.7.006"
        val failedComputer = Computer(ip, handler, -1, null)
        failedComputer.LOAD_FAILURE = true
        failedComputer.errorMessage = "Unable to load local account data for ip=$ip. No user.stats row found."
        handler.addComputer(failedComputer)

        handler.addData(null, ip)
        processTask(handler, pollTask(handler) ?: error("expected unload task"))
        assertNull(handler.getComputer(ip))

        handler.addData(ApplicationData(RequestWebPagePayload(HashMap()), 0, requesterIp), ip)
        processTask(handler, pollTask(handler) ?: error("expected request task"))

        assertNull(handler.getComputer(ip))

        val webpageTask = pollTask(handler)
        val webpagePayload = taskPayload(webpageTask) as ApplicationData
        assertEquals(requesterIp, taskIp(webpageTask))
        assertEquals("webpage", webpagePayload.command.wireName())
        assertEquals("Server Not Found", (webpagePayload.payload as WebPagePayload).title)

        val messageTask = pollTask(handler)
        val messagePayload = taskPayload(messageTask) as ApplicationData
        assertEquals(requesterIp, taskIp(messageTask))
        assertEquals("message", messagePayload.command.wireName())

        assertFalse(hasPendingTasks(handler))
    }

    private fun pollTask(handler: ComputerHandler): Any? {
        val method = handler.javaClass.getDeclaredMethod("pollTask")
        method.isAccessible = true
        return method.invoke(handler)
    }

    private fun processTask(handler: ComputerHandler, task: Any) {
        val method = handler.javaClass.getDeclaredMethod("processTask", task.javaClass)
        method.isAccessible = true
        method.invoke(handler, task)
    }

    private fun taskPayload(task: Any?): Any? {
        if (task == null) return null
        val field = task.javaClass.getDeclaredField("applicationData")
        field.isAccessible = true
        return field.get(task)
    }

    private fun taskIp(task: Any?): String? {
        if (task == null) return null
        val field = task.javaClass.getDeclaredField("ip")
        field.isAccessible = true
        return field.get(task) as String?
    }

    private fun hasPendingTasks(handler: ComputerHandler): Boolean {
        return pollTask(handler) != null
    }
}
