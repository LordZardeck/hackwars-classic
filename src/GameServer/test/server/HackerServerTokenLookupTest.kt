package server

import com.plink.dolphinnet.MessageServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HackerServerTokenLookupTest {
    @Test
    fun cryptFallsBackToBareTokenLookupWhenClientHashDiffers() {
        val messageServer = MessageServer(25, 0)
        try {
            val hackerServer = HackerServer(messageServer, "server-1")
            val token = hackerServer.getRandomKey("10.0.0.1", "client-a", null)[0] as String

            assertNotEquals("10.0.0.1", token)
            assertEquals("10.0.0.1", hackerServer.resolveEncryptedIp(token))
            assertEquals("10.0.0.1", hackerServer.crypt(token, "client-a"))
            assertEquals("10.0.0.1", hackerServer.crypt(token, "client-b"))
        } finally {
            messageServer.close()
        }
    }

    @Test
    fun removeRandomKeyClearsBareTokenFallback() {
        val messageServer = MessageServer(25, 0)
        try {
            val hackerServer = HackerServer(messageServer, "server-1")
            val token = hackerServer.getRandomKey("10.0.0.2", "client-a", null)[0] as String

            hackerServer.removeRandomKey("10.0.0.2")

            assertEquals(null, hackerServer.resolveEncryptedIp(token))
            assertEquals(token, hackerServer.crypt(token, "client-a"))
            assertEquals(token, hackerServer.crypt(token, "client-b"))
        } finally {
            messageServer.close()
        }
    }
}
