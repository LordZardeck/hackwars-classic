package game

import com.hackwars.data.model.ForumActivity
import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class CheckOutHandlerTest {
    @Test
    fun processWork_routesToXmlRpcWhenThePageChanged() {
        val auth = FakeAuthDataService()
        val profile = FakeProfileDataService()
        val handler = RecordingCheckOutHandler(auth, profile)
        val work = arrayOf<Any?>("10.0.0.1", mock<Computer>(), "token", true, "Title", "Body")

        handler.processWork(work)

        assertEquals("xmlrpc", handler.branch)
        assertEquals("10.0.0.1", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    @Test
    fun processWork_insertsWhenTheProfileDoesNotExist() {
        val auth = FakeAuthDataService()
        val profile = FakeProfileDataService(xmlByIp = mutableMapOf("10.0.0.2" to null))
        val handler = RecordingCheckOutHandler(auth, profile)
        val work = arrayOf<Any?>("10.0.0.2", mock<Computer>(), "token", false, "Title", "Body")

        handler.processWork(work)

        assertEquals("insert", handler.branch)
        assertEquals("10.0.0.2", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    @Test
    fun processWork_updatesWhenTheProfileAlreadyExists() {
        val auth = FakeAuthDataService()
        val profile = FakeProfileDataService(xmlByIp = mutableMapOf("10.0.0.3" to "existing"))
        val handler = RecordingCheckOutHandler(auth, profile)
        val work = arrayOf<Any?>("10.0.0.3", mock<Computer>(), "token", false, "Title", "Body")

        handler.processWork(work)

        assertEquals("update", handler.branch)
        assertEquals("10.0.0.3", handler.ip)
        assertEquals("xml-data", handler.content)
    }

    @Test
    fun fetchProfile_marksInactiveForumUsers() {
        val auth = FakeAuthDataService(
            activity = ForumActivity(
                npc = "N",
                daysSinceLastLogin = 15,
            )
        )
        val profile = FakeProfileDataService()
        val handler = CheckOutHandler(auth, profile)

        assertEquals("inactive", handler.fetchProfile("10.0.0.4", checkActive = true))
    }

    private class RecordingCheckOutHandler(
        auth: GameAuthDataService,
        profile: GameProfileDataService,
    ) : CheckOutHandler(auth, profile) {
        var branch = ""
        var ip = ""
        var content = ""

        override fun readComputerOutput(computer: Computer): String {
            return "xml-data"
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

    private class FakeAuthDataService(
        val authenticateResult: Boolean = true,
        val activity: ForumActivity? = null,
    ) : GameAuthDataService {
        override fun authenticate(username: String, loginPassword: String, passwordIniValue: String?): Boolean {
            return authenticateResult
        }

        override fun findForumLoginSnapshotByName(username: String) = null

        override fun findForumActivityByIp(ip: String): ForumActivity? = activity

        override fun markForumLoginNow(username: String) {
        }
    }

    private class FakeProfileDataService(
        val xmlByIp: MutableMap<String, String?> = mutableMapOf(),
    ) : GameProfileDataService {
        override fun findProfileXmlByIp(ip: String): String? {
            return xmlByIp[ip]
        }

        override fun upsertProfileXmlByIp(ip: String, xml: String) {
            xmlByIp[ip] = xml
        }
    }
}
