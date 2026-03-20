package game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CheckOutHandlerTest {
    @Test
    fun processWork_routesToXmlRpcWhenThePageChanged() {
        val handler = RecordingCheckOutHandler(profileLookup = null)
        val work = arrayOf<Any?>("10.0.0.1", mock<Computer>(), "token", true, "Title", "Body")

        handler.processWork(work)

        assertEquals("xmlrpc", handler.branch)
        assertEquals("10.0.0.1", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    @Test
    fun processWork_insertsWhenTheProfileDoesNotExist() {
        val handler = RecordingCheckOutHandler(profileLookup = null)
        val work = arrayOf<Any?>("10.0.0.2", mock<Computer>(), "token", false, "Title", "Body")

        handler.processWork(work)

        assertEquals("insert", handler.branch)
        assertEquals("10.0.0.2", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    @Test
    fun processWork_updatesWhenTheProfileAlreadyExists() {
        val handler = RecordingCheckOutHandler(profileLookup = "existing")
        val work = arrayOf<Any?>("10.0.0.3", mock<Computer>(), "token", false, "Title", "Body")

        handler.processWork(work)

        assertEquals("update", handler.branch)
        assertEquals("10.0.0.3", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    private class RecordingCheckOutHandler(
        private val profileLookup: String?
    ) : CheckOutHandler() {
        var branch = ""
        var ip = ""
        var content = ""

        override fun readComputerOutput(computer: Computer): String {
            return "xml-data"
        }

        override fun fetchProfile(ip: String, checkActive: Boolean): String? {
            return profileLookup
        }

        override fun saveProfileViaXmlRpc(
            ip: String,
            content: String,
            pageChanged: Boolean,
            pageTitle: String,
            pageBody: String
        ) {
            branch = "xmlrpc"
            this.ip = ip
            this.content = content
        }

        override fun insertProfile(ip: String, content: String) {
            branch = "insert"
            this.ip = ip
            this.content = content
        }

        override fun updateProfile(ip: String, content: String) {
            branch = "update"
            this.ip = ip
            this.content = content
        }
    }
}
