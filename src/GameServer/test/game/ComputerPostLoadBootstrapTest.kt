package game

import game.payload.RequestWebPagePayload
import game.payload.WebPagePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ComputerPostLoadBootstrapTest {
    @Test
    fun `failed remote load is unloaded immediately during post load bootstrap`() {
        val handler = ComputerHandler(null)
        val computer = Computer("188.230.7.189", handler, -1, null)
        val requesterIp = "900.800.7.006"

        computer.Loaded = true
        computer.Loading = false
        computer.LOAD_FAILURE = true
        computer.errorMessage = "Unable to load local account data for ip=188.230.7.189. No user.stats row found."
        computer.loadRequester = requesterIp
        computer.addData(ApplicationData(RequestWebPagePayload(HashMap()), 0, requesterIp))

        ComputerPostLoadBootstrap().apply(computer)

        assertFalse(computer.Loaded)

        val firstTask = pollTask(handler)
        val firstPayload = taskPayload(firstTask)
        assertNotNull(firstPayload)
        assertEquals(requesterIp, taskIp(firstTask))
        assertEquals("webpage", (firstPayload as ApplicationData).command.wireName())
        assertEquals("Server Not Found", (firstPayload.payload as WebPagePayload).title)

        val secondTask = pollTask(handler)
        val secondPayload = taskPayload(secondTask) as ApplicationData
        assertEquals(requesterIp, taskIp(secondTask))
        assertEquals("message", secondPayload.command.wireName())

        val thirdTask = pollTask(handler)
        assertEquals("188.230.7.189", taskIp(thirdTask))
        assertEquals(null, taskPayload(thirdTask))
    }

    private fun pollTask(handler: ComputerHandler): Any? {
        val method = handler.javaClass.getDeclaredMethod("pollTask")
        method.isAccessible = true
        return method.invoke(handler)
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
}
