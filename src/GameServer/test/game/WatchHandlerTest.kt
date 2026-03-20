package game

import com.hackwars.game.program.Program
import game.payload.DamagePayload
import game.payload.WatchXpPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WatchHandlerTest {
    @Test
    fun checkWatches_queuesTypedWatchXpPayloadForHealthWatchTrigger() {
        val computer = mock<Computer>()
        val computerHandler = mock<ComputerHandler>()
        val port = mock<Port>()

        whenever(computer.getIP()).thenReturn("10.0.0.1")
        whenever(computer.watchLevel).thenReturn(20.0f)
        whenever(port.getOverHeated()).thenReturn(false)
        whenever(port.getHealth()).thenReturn(5.0f)
        whenever(port.getNumber()).thenReturn(7)

        val watch = Watch(computer).apply {
            type = WatchHandler.HEALTH
            on = true
            this.port = 7
            quantity = 10.0f
            initialQuantity = 20.0f
            actualCpuCost = 1.25f
            program = object : Program(computer, null) {
                override fun installScript(script: HashMap<*, *>) = Unit

                override fun execute(applicationData: ApplicationData) = Unit

                override fun getTypeKeys(): Array<String?> = arrayOf()

                override fun getContent(): HashMap<*, *> = HashMap<Any?, Any?>()

                override fun outputXML(): String = ""
            }
        }

        val handler = WatchHandler(computer, computerHandler)
        handler.addWatch(watch)

        val watchCost = handler.checkWatches(
            ApplicationData(
                DamagePayload(
                    damage = 12.0f,
                    targetIp = "9.9.9.9",
                    targetPort = 7,
                    damageFromFireWall = false,
                    zombieSource = null,
                    windowHandle = 0,
                    commodityId = -1
                ),
                7,
                "9.9.9.9"
            ),
            hashMapOf(7 to port),
            0.0f
        )

        assertEquals(1.25f, watchCost, 0.0001f)

        val captured = argumentCaptor<ApplicationData>()
        verify(computerHandler).addData(captured.capture(), eq("10.0.0.1"))

        val queued = captured.firstValue
        assertEquals("watchxp", queued.command.wireName())
        assertTrue(queued.payload is WatchXpPayload)
        assertEquals(20.0f, (queued.payload as WatchXpPayload).amount, 0.0001f)
    }
}
