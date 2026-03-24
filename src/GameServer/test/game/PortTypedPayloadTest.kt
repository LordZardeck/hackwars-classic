package game

import com.hackwars.rpc.SaveFile
import game.payload.LocalPortEntryPayload
import game.payload.FinalizePutPayload
import game.payload.StructuredMessagePayload
import game.payload.ZombieAttackPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor

class PortTypedPayloadTest {
    @Test
    fun friendlyPut_emitsTypedSaveFilePayload() {
        val computer = mock(Computer::class.java)
        val computerHandler = mock(NetworkSwitch::class.java)
        computer.ip = "10.0.0.1"

        val port = Port(computer, computerHandler)
        val file = HackerFile(HackerFile.HTTP)
        file.name = "artifact"

        port.friendlyPut(
            ApplicationData(
                FinalizePutPayload("10.0.0.2", "artifact", "Public/", "Store/", "password", file),
                0,
                "source-ip"
            )
        )

        val dataCaptor = argumentCaptor<ApplicationData>()
        val targetCaptor = argumentCaptor<String>()
        verify(computerHandler, org.mockito.Mockito.times(2)).addData(dataCaptor.capture(), targetCaptor.capture())

        assertEquals("source-ip", targetCaptor.allValues[0])
        assertEquals("source-ip", targetCaptor.allValues[1])
        assertTrue(dataCaptor.allValues[0].payload is StructuredMessagePayload)
        val saveFile = dataCaptor.allValues[1].payload as SaveFile
        assertEquals("source-ip", saveFile.ip)
        assertEquals("Public/", saveFile.path)
        assertEquals(file, saveFile.name)
    }

    @Test
    fun addApplicationData_attackWhileBusy_emitsTypedStructuredMessagePayload() {
        val computer = mock(Computer::class.java)
        val computerHandler = mock(NetworkSwitch::class.java)
        computer.ip = "10.0.0.1"
        `when`(computer.getNetwork()).thenReturn("net-1")

        val port = Port(computer, computerHandler)
        port.on = true
        port.type = Port.ATTACK
        port.number = 22
        Port::class.java.getDeclaredField("_accessing").apply {
            isAccessible = true
            set(port, "busy-ip")
        }

        port.addApplicationData(
            ApplicationData(
                LocalPortEntryPayload(ApplicationCommand.of("attack"), "net-1"),
                0,
                "attacker-ip"
            ),
            0
        )

        val dataCaptor = argumentCaptor<ApplicationData>()
        val targetCaptor = argumentCaptor<String>()
        verify(computerHandler).addData(dataCaptor.capture(), targetCaptor.capture())

        assertEquals("attacker-ip", targetCaptor.firstValue)
        assertEquals("message", dataCaptor.firstValue.command.wireName())
        val payload = dataCaptor.firstValue.payload as StructuredMessagePayload
        assertTrue(payload.message[0] === MessageHandler.PORT_ALREADY_UNDER_ATTACK)
        assertEquals(22, payload.parameters!![0])
        assertEquals("10.0.0.1", payload.parameters!![1])
        assertEquals("busy-ip", payload.parameters!![2])
    }

    @Test
    fun addApplicationData_zombieAttackWhileOverheated_emitsOverheatMessages() {
        val computer = mock(Computer::class.java)
        val computerHandler = mock(NetworkSwitch::class.java)
        computer.ip = "10.0.0.1"

        val port = Port(computer, computerHandler)
        port.on = true
        port.overHeated = true
        port.number = 19

        port.addApplicationData(
            ApplicationData(
                ZombieAttackPayload(
                    targetIp = "10.0.0.2",
                    targetPort = 22,
                    sourceIp = "source-ip",
                    sourcePort = 7,
                    secondaryPorts = null,
                    scripts = null,
                    extraInfo = null,
                    parentIp = null
                ),
                0,
                "source-ip"
            ),
            0
        )

        val dataCaptor = argumentCaptor<ApplicationData>()
        val targetCaptor = argumentCaptor<String>()
        verify(computerHandler, org.mockito.Mockito.times(2)).addData(dataCaptor.capture(), targetCaptor.capture())

        assertEquals("source-ip", targetCaptor.allValues[0])
        assertEquals("10.0.0.1", targetCaptor.allValues[1])
        assertTrue(dataCaptor.allValues[0].payload is StructuredMessagePayload)
        assertTrue(dataCaptor.allValues[1].payload is StructuredMessagePayload)
    }
}
