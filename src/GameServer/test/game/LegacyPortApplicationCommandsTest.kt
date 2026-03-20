package game

import assignments.PacketAssignment
import assignments.PacketPort
import com.hackwars.rpc.DeleteFirewall
import com.hackwars.rpc.FetchPorts
import com.hackwars.rpc.InstallApplication
import com.hackwars.rpc.InstallFirewall
import com.hackwars.rpc.ReplaceApplication
import com.hackwars.rpc.RequestSecondaryDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verifyNoInteractions
import java.lang.reflect.Field
import java.util.HashMap

class LegacyPortApplicationCommandsTest {
    private val handler = LegacyPortApplicationCommands()

    @Test
    fun dispatch_requestSecondaryDirectory_returnsFalse() {
        val fixture = baseComputer("1.1.1.1")

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(RequestSecondaryDirectory("1.1.1.1", "Public/", "9.9.9.9", 7), 13, "1.1.1.1"),
            13
        )

        assertFalse(handled)
    }

    @Test
    fun dispatch_fetchPorts_populatesPacketAssignment() {
        val fixture = baseComputer("2.2.2.2")
        val packetPort = PacketPort()
        packetPort.setNumber(45)
        val port = mock(Port::class.java)
        `when`(port.getPacketPort()).thenReturn(packetPort)
        fixture.ports[45] = port

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(FetchPorts("2.2.2.2"), 0, "2.2.2.2"),
            0
        )

        assertTrue(handled)
        assertTrue(fixture.computer.systemChange)
        assertEquals(1, fixture.packetAssignment.getPacketPorts().size)
        assertSame(packetPort, fixture.packetAssignment.getPacketPorts()[0])
    }

    @Test
    fun dispatch_deleteFirewall_movesFirewallToDiskAndRefreshesPorts() {
        val fixture = baseComputer("3.3.3.3")
        `when`(fixture.fileSystem.getSpaceLeft()).thenReturn(1)
        val installed = HackerFile(HackerFile.NEW_FIREWALL)
        installed.setName("Shield")
        installed.setQuantity(0)

        val firewall = mock(NewFireWall::class.java)
        `when`(firewall.getHackerFile()).thenReturn(installed)

        val port = mock(Port::class.java)
        `when`(port.getNumber()).thenReturn(22)
        `when`(port.getFireWall()).thenReturn(firewall)
        fixture.ports[22] = port

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(DeleteFirewall("3.3.3.3", 22), 0, "3.3.3.3"),
            0
        )

        assertTrue(handled)
        verify(fixture.fileSystem).addFile(installed, false)
        val firewallCaptor = argumentCaptor<HackerFile>()
        verify(port).setFireWall(firewallCaptor.capture())
        assertEquals("None", firewallCaptor.firstValue.getName())
        assertNotNull(firewallCaptor.firstValue)
        val messageCaptor = argumentCaptor<Array<out Any?>>()
        val parameterCaptor = argumentCaptor<Array<Any?>>()
        verify(fixture.computer).addMessage(messageCaptor.capture(), parameterCaptor.capture())
        assertSame(MessageHandler.REMOVE_FIREWALL_SUCCESS, messageCaptor.firstValue)
        assertEquals("Shield", parameterCaptor.firstValue[0])
        assertFetchPortsRefresh(fixture.networkSwitch, "3.3.3.3")
    }

    @Test
    fun dispatch_installFirewall_whenLevelTooLow_addsFailureAndRefreshesPorts() {
        val fixture = baseComputer("4.4.4.4")
        val firewallFile = HackerFile(HackerFile.NEW_FIREWALL)
        val content = HashMap<Any?, Any?>()
        content["equip_level"] = "5"
        firewallFile.setContent(content)
        `when`(fixture.fileSystem.getFile("Public/", "wall")).thenReturn(firewallFile)
        fixture.stats["FireWall"] = 0.0f
        doReturn(0).`when`(fixture.computer).getLevel(anyFloat())

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(InstallFirewall("4.4.4.4", 17, "Public/", "wall"), 17, "4.4.4.4"),
            17
        )

        assertTrue(handled)
        val messageCaptor = argumentCaptor<Array<out Any?>>()
        val parameterCaptor = argumentCaptor<Array<Any?>>()
        verify(fixture.computer).addMessage(messageCaptor.capture(), parameterCaptor.capture())
        assertSame(MessageHandler.INSTALL_FIREWALL_FAIL_LEVEL, messageCaptor.firstValue)
        assertEquals(5, parameterCaptor.firstValue[0])
        assertFetchPortsRefresh(fixture.networkSwitch, "4.4.4.4")
    }

    @Test
    fun dispatch_installApplication_whenMemoryIsFull_addsFailureWithoutRefresh() {
        val fixture = baseComputer("5.5.5.5")

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(InstallApplication("5.5.5.5", Computer.MEMORY_CHART[0].toInt(), "Public/", "bank"), 0, "5.5.5.5"),
            Computer.MEMORY_CHART[0].toInt()
        )

        assertTrue(handled)
        assertTrue(fixture.computer.systemChange)
        verify(fixture.computer).addMessage(MessageHandler.MAX_PROGRAMS_REACHED)
        verifyNoInteractions(fixture.networkSwitch)
    }

    @Test
    fun dispatch_installApplication_createsDefaultBankPortAndRefreshes() {
        val fixture = baseComputer("5.5.5.6")
        val application = HackerFile(HackerFile.BANKING_COMPILED)
        application.setQuantity(2)
        application.setCPUCost(0f)
        val content = HashMap<Any?, Any?>()
        content["deposit"] = "dep"
        content["withdraw"] = "wd"
        content["transfer"] = "tr"
        application.setContent(content)
        `when`(fixture.fileSystem.getFile("Public/", "bank")).thenReturn(application)

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(InstallApplication("5.5.5.6", 6, "Public/", "bank"), 6, "5.5.5.6"),
            6
        )

        assertTrue(handled)
        assertEquals(6, fixture.computer.defaultBank)
        assertTrue(fixture.ports.containsKey(6))
        val installedPort = fixture.ports[6] as Port
        assertEquals(6, installedPort.getNumber())
        assertEquals(Port.BANKING, installedPort.getType())
        assertTrue(installedPort.getOn())
        assertEquals(1, application.getQuantity())
        assertFetchPortsRefresh(fixture.networkSwitch, "5.5.5.6")
    }

    @Test
    fun dispatch_replaceApplication_whenFileMissing_stillRefreshesPorts() {
        val fixture = baseComputer("6.6.6.6")
        `when`(fixture.fileSystem.getFile("Public/", "ftp")).thenReturn(null)

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(ReplaceApplication("6.6.6.6", 18, "Public/", "ftp"), 18, "6.6.6.6"),
            18
        )

        assertTrue(handled)
        assertFetchPortsRefresh(fixture.networkSwitch, "6.6.6.6")
    }

    @Test
    fun dispatch_replaceApplication_whenPortIsUnderAttack_addsFailureAndLeavesFileUntouched() {
        val fixture = baseComputer("6.6.6.7")
        val application = HackerFile(HackerFile.BANKING_COMPILED)
        application.setQuantity(2)
        application.setCPUCost(0f)
        val content = HashMap<Any?, Any?>()
        content["deposit"] = "dep"
        content["withdraw"] = "wd"
        content["transfer"] = "tr"
        application.setContent(content)
        `when`(fixture.fileSystem.getFile("Public/", "bank")).thenReturn(application)

        val port = mock(Port::class.java)
        `when`(port.getNumber()).thenReturn(18)
        `when`(port.getBaseCPUCost()).thenReturn(0f)
        `when`(port.getAccessing()).thenReturn("attacker")
        `when`(port.getAttacking()).thenReturn(false)
        `when`(port.getOverHeated()).thenReturn(false)
        fixture.ports[18] = port

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(ReplaceApplication("6.6.6.7", 18, "Public/", "bank"), 18, "6.6.6.7"),
            18
        )

        assertTrue(handled)
        assertEquals(2, application.getQuantity())
        verify(fixture.computer).addMessage(MessageHandler.REPLACE_APPLICATION_UNDER_ATTACK)
        assertFetchPortsRefresh(fixture.networkSwitch, "6.6.6.7")
    }

    @Test
    fun dispatch_installFirewall_replacesExistingFirewallAndRefreshes() {
        val fixture = baseComputer("6.6.6.8")
        val firewallFile = HackerFile(HackerFile.NEW_FIREWALL)
        firewallFile.setQuantity(2)
        firewallFile.setCPUCost(0f)
        val content = HashMap<Any?, Any?>()
        content["equip_level"] = "0"
        firewallFile.setContent(content)
        `when`(fixture.fileSystem.getFile("Public/", "wall")).thenReturn(firewallFile)
        fixture.stats["FireWall"] = 0.0f
        doReturn(0).`when`(fixture.computer).getLevel(anyFloat())

        val installed = HackerFile(HackerFile.NEW_FIREWALL)
        installed.setName("OldWall")
        installed.setQuantity(0)
        val existingFirewall = mock(NewFireWall::class.java)
        `when`(existingFirewall.getHackerFile()).thenReturn(installed)

        val port = mock(Port::class.java)
        `when`(port.getNumber()).thenReturn(19)
        `when`(port.getFireWall()).thenReturn(existingFirewall)
        fixture.ports[19] = port

        val handled = handler.dispatch(
            fixture.computer,
            ApplicationData(InstallFirewall("6.6.6.8", 19, "Public/", "wall"), 19, "6.6.6.8"),
            19
        )

        assertTrue(handled)
        assertEquals(1, firewallFile.getQuantity())
        verify(fixture.fileSystem).addFile(installed, false)
        verify(port).setFireWall(firewallFile)
        val messageCaptor = argumentCaptor<Array<out Any?>>()
        val parameterCaptor = argumentCaptor<Array<Any?>>()
        verify(fixture.computer).addMessage(messageCaptor.capture(), parameterCaptor.capture())
        assertSame(MessageHandler.FIREWALL_REPLACED, messageCaptor.firstValue)
        assertEquals("OldWall", parameterCaptor.firstValue[0])
        assertFetchPortsRefresh(fixture.networkSwitch, "6.6.6.8")
    }

    private data class ComputerFixture(
        val computer: Computer,
        val packetAssignment: PacketAssignment,
        val ports: HashMap<Any?, Any?>,
        val stats: HashMap<Any?, Any?>,
        val networkSwitch: NetworkSwitch,
        val fileSystem: FileSystem,
        val equipmentSheet: EquipmentSheet
    )

    private fun baseComputer(ip: String): ComputerFixture {
        val computer = mock(Computer::class.java, CALLS_REAL_METHODS)
        val packetAssignment = PacketAssignment(0)
        val ports = HashMap<Any?, Any?>()
        val stats = HashMap<Any?, Any?>()
        val networkSwitch = mock(NetworkSwitch::class.java)
        val fileSystem = mock(FileSystem::class.java)
        val equipmentSheet = mock(EquipmentSheet::class.java)

        setField(computer, "ip", ip)
        setField(computer, "PA", packetAssignment)
        setField(computer, "Ports", ports)
        setField(computer, "Stats", stats)
        setField(computer, "MyComputerHandler", networkSwitch)
        setField(computer, "MyFileSystem", fileSystem)
        setField(computer, "MyEquipmentSheet", equipmentSheet)
        setField(computer, "Choices", java.util.ArrayList<Any?>())
        setField(computer, "cputype", 0)
        setField(computer, "memorytype", 0)
        setField(computer, "connectionID", -1)

        `when`(equipmentSheet.getCPUBonus()).thenReturn(0f)

        return ComputerFixture(computer, packetAssignment, ports, stats, networkSwitch, fileSystem, equipmentSheet)
    }

    private fun assertFetchPortsRefresh(networkSwitch: NetworkSwitch, ip: String) {
        val captor = argumentCaptor<ApplicationData>()
        val ipCaptor = argumentCaptor<String>()
        verify(networkSwitch).addData(captor.capture(), ipCaptor.capture())
        assertEquals(ip, ipCaptor.firstValue)
        assertEquals("fetchports", captor.firstValue.command.wireName())
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field: Field = type.getDeclaredField(name)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException(name)
    }
}
