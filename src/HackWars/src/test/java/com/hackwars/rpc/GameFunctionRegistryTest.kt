package com.hackwars.rpc

import org.junit.Assert.*
import org.junit.Test

class GameFunctionRegistryTest {
    @Test
    fun everyRegisteredFunctionHasUniqueWireName() {
        val wireNames = GameFunctions.ALL.map { it.wireName }

        assertEquals(wireNames.size, wireNames.toSet().size)
    }

    @Test
    fun byWireNameResolvesEveryRegisteredFunction() {
        GameFunctions.ALL.forEach { spec ->
            assertSame(spec, GameFunctions.byWireName(spec.wireName))
        }
    }

    @Test
    fun representativeFunctionsRoundTripThroughRegistry() {
        assertRoundTrip(GameFunctions.CHANGENETWORK, ChangeNetwork("encrypted-ip", "corp-net")) { parsed ->
            assertEquals("encrypted-ip", parsed.encryptedIp)
            assertEquals("corp-net", parsed.network)
        }

        assertRoundTrip(GameFunctions.REQUESTDIRECTORY, RequestDirectory("encrypted-ip", "/home")) { parsed ->
            assertEquals("encrypted-ip", parsed.ip)
            assertEquals("/home", parsed.path)
        }

        assertRoundTrip(GameFunctions.REQUESTEQUIPMENT, RequestEquipment("encrypted-ip")) { parsed ->
            assertEquals("encrypted-ip", parsed.ip)
        }

        assertRoundTrip(GameFunctions.REQUESTSCAN, RequestScan("encrypted-ip", "target-ip")) { parsed ->
            assertEquals("encrypted-ip", parsed.ip)
            assertEquals("target-ip", parsed.targetIP)
        }

        val parameters = hashMapOf<Any?, Any?>("page" to 2, "store" to "store1")
        assertRoundTrip(GameFunctions.REQUESTWEBPAGE, RequestWebpage("target-ip", "source-ip", parameters)) { parsed ->
            assertEquals("target-ip", parsed.targetIp)
            assertEquals("source-ip", parsed.sourceIp)
            assertEquals(parameters, parsed.parameters)
        }

        val secondaryPorts = arrayOf<Int?>(25, null, 443)
        val scripts = arrayOf<Array<String?>?>(arrayOf("scan", "attack"), null)
        val extraInfo = arrayOf<Any?>("window", 7)
        assertRoundTrip(
            GameFunctions.REQUESTATTACK,
            RequestAttack("target-ip", 21, "source-ip", 22, secondaryPorts, scripts, extraInfo, 77)
        ) { parsed ->
            assertEquals("target-ip", parsed.targetIP)
            assertEquals(21, parsed.targetPort)
            assertEquals("source-ip", parsed.sourceIP)
            assertEquals(22, parsed.sourcePort)
            assertArrayEquals(secondaryPorts, parsed.secondaryPorts)
            assertNotNull(parsed.scripts)
            assertArrayEquals(scripts[0], parsed.scripts!![0])
            assertEquals(null, parsed.scripts!![1])
            assertArrayEquals(extraInfo, parsed.extraInfo)
            assertEquals(77, parsed.windowHandle)
        }

        assertRoundTrip(GameFunctions.REQUESTCANCELATTACK, RequestCancelAttack("encrypted-ip", 81)) { parsed ->
            assertEquals("encrypted-ip", parsed.ip)
            assertEquals(81, parsed.port)
        }

        val allFiles = arrayOf<Any?>("virus.exe", 3, HashMap<Any?, Any?>().apply { put("path", "/tmp") })
        assertRoundTrip(GameFunctions.SELLFILEMULTI, SellFileMulti("encrypted-ip", allFiles)) { parsed ->
            assertEquals("encrypted-ip", parsed.ip)
            assertArrayEquals(allFiles, parsed.allFiles)
        }
    }

    private fun <T : RemoteFunctionCallImpl> assertRoundTrip(
        spec: GameFunctionSpec,
        payload: T,
        assertions: (T) -> Unit
    ) {
        val rpc = payload.toRfc(42)

        assertEquals(42, rpc.id)
        assertEquals(spec.wireName, rpc.function)
        assertSame(spec, GameFunctions.byWireName(rpc.function))

        @Suppress("UNCHECKED_CAST")
        val parsed = spec.fromRpc(rpc) as T
        assertions(parsed)
    }
}
