package game.computer.session

import com.hackwars.data.model.ForumLoginSnapshot
import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import com.hackwars.data.service.GameTelemetryDataService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage

class ComputerSessionServiceTest {
    private var previousBaseUrl: String? = null

    @Before
    fun captureBaseUrlProperty() {
        previousBaseUrl = System.getProperty("hackwars.localWebBaseUrl")
    }

    @After
    fun restoreBaseUrlProperty() {
        if (previousBaseUrl == null) {
            System.clearProperty("hackwars.localWebBaseUrl")
        } else {
            System.setProperty("hackwars.localWebBaseUrl", previousBaseUrl)
        }
    }

    @Test
    fun `play fab auth short circuits and requests preferences`() {
        val auth = FakeAuthDataService()
        val service = service(auth = auth)

        val result = service.authenticate(
            LoginRequest(
                ip = "10.0.0.1",
                userName = null,
                loginPassword = "",
                playFabAuthenticated = true,
                testing = false,
            )
        )

        assertTrue(result.accepted)
        assertTrue(result.sendPreferences)
        assertTrue(auth.authenticateCalls.isEmpty())
    }

    @Test
    fun `xor crypt round trips data with the same key`() {
        val service = service()

        val original = "hello".toByteArray()
        val encrypted = original.copyOf()
        val crypted = service.xorCrypt(encrypted, "key")
        val decrypted = service.xorCrypt(crypted.toByteArray(), "key")

        assertEquals("hello", decrypted)
    }

    @Test
    fun `local auth fallback short circuits when enabled`() {
        val auth = FakeAuthDataService()
        val service = service(
            config = ComputerSessionConfig(localAuthFallbackEnabled = true),
            auth = auth,
        )

        val result = service.authenticate(
            LoginRequest(
                ip = "10.0.0.1",
                userName = "player",
                loginPassword = "pw",
                playFabAuthenticated = false,
                testing = false,
            )
        )

        assertTrue(result.accepted)
        assertTrue(result.sendPreferences)
        assertTrue(auth.authenticateCalls.isEmpty())
    }

    @Test
    fun `password ini shortcut selects forum auth path and validates ip`() {
        val auth = FakeAuthDataService(
            authenticateResult = true,
            forumLoginSnapshot = ForumLoginSnapshot(
                ip = "10.0.0.1",
                npc = "N",
                daysSinceLastLogin = 0,
            ),
        )
        val service = service(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            auth = auth,
            passwordSource = StaticPasswordSource("secret"),
        )

        val result = service.authenticate(
            LoginRequest(
                ip = "10.0.0.1",
                userName = "player",
                loginPassword = "secret",
                playFabAuthenticated = false,
                testing = false,
            )
        )

        assertTrue(result.accepted)
        assertTrue(result.sendPreferences)
        assertEquals(
            listOf(
                AuthenticateCall(
                    username = "player",
                    loginPassword = "secret",
                    passwordIniValue = "secret",
                ),
            ),
            auth.authenticateCalls
        )
        assertEquals(listOf("player"), auth.markedForumLogins)
    }

    @Test
    fun `wrong ip rejects successful db auth`() {
        val auth = FakeAuthDataService(
            authenticateResult = true,
            forumLoginSnapshot = ForumLoginSnapshot(
                ip = "10.0.0.2",
                npc = "N",
                daysSinceLastLogin = 0,
            ),
        )
        val service = service(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            auth = auth,
            passwordSource = StaticPasswordSource("different"),
        )

        val result = service.authenticate(
            LoginRequest(
                ip = "10.0.0.1",
                userName = "player",
                loginPassword = "pw",
                playFabAuthenticated = false,
                testing = false,
            )
        )

        assertFalse(result.accepted)
        assertFalse(result.sendPreferences)
        assertEquals(listOf("player"), auth.markedForumLogins)
    }

    @Test
    fun `remote function packs expands upgrade limits`() {
        val gateway = RecordingXmlRpcGateway(
            response = arrayOf("ok", "ignored", true, false)
        )
        val service = service(
            config = ComputerSessionConfig(remoteXmlRpcEnabled = true),
            xmlRpcGateway = gateway,
        )

        val result = service.requestFunctionPacks("10.0.0.1")

        assertTrue(result.enabled)
        assertTrue(result.upgradedAccount)
        assertFalse(result.inactive)
        assertEquals(16384, result.maxOps)
        assertEquals(240000, result.fileSizeLimit)
    }

    @Test
    fun `captcha challenge returns pixels and key`() {
        val image = BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x00FF00)
        val service = service(
            captchaImageSource = StaticCaptchaImageSource(image),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("67890"),
        )

        val result = service.generateCaptcha()

        assertEquals("67890", result.key)
        assertEquals(175 * 45, result.pixels?.size)
    }

    @Test
    fun `ping timeout records play statistics and resets timestamps`() {
        val telemetry = FakeTelemetryDataService()
        val service = service(telemetry = telemetry)

        val result = service.recordPlayStatistics(
            PlayStatisticsRequest(
                loggedIn = true,
                ip = "10.0.0.1",
                currentTimeMillis = 25000,
                logInTimeMillis = 1000,
                lastPingTimeMillis = 1000,
                lastClientPacketTimeMillis = 0,
            )
        )

        assertTrue(result.pingRecorded)
        assertEquals(0, result.lastPingTimeMillis)
        assertEquals(0, result.logInTimeMillis)
        assertEquals(listOf(PlayWindow("10.0.0.1", 1000, 1000)), telemetry.windows)
    }

    @Test
    fun `client packet timeout records play statistics and resets timestamps`() {
        val telemetry = FakeTelemetryDataService()
        val service = service(telemetry = telemetry)

        val result = service.recordPlayStatistics(
            PlayStatisticsRequest(
                loggedIn = true,
                ip = "10.0.0.1",
                currentTimeMillis = 700000,
                logInTimeMillis = 1000,
                lastPingTimeMillis = 0,
                lastClientPacketTimeMillis = 1000,
            )
        )

        assertTrue(result.clientPacketRecorded)
        assertEquals(0, result.lastClientPacketTimeMillis)
        assertEquals(0, result.logInTimeMillis)
        assertEquals(listOf(PlayWindow("10.0.0.1", 1000, 1000)), telemetry.windows)
    }

    @Test
    fun `load local save xml falls back to the database when the endpoint returns an error`() {
        val saveXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <save><ip>10.0.0.1</ip></save>
        """.trimIndent()
        val profile = FakeProfileDataService(
            xmlByIp = mutableMapOf("10.0.0.1" to saveXml)
        )

        try {
            System.setProperty(
                "hackwars.localWebBaseUrl",
                "http://127.0.0.1:1/hackwars",
            )

            val service = service(
                config = ComputerSessionConfig(localAuthFallbackEnabled = false),
                profile = profile,
            )

            val xml = service.loadLocalSaveXml("10.0.0.1", active = false)

            assertTrue(xml.contains("<ip>10.0.0.1</ip>"))
            assertEquals(listOf("10.0.0.1"), profile.readIps)
        } finally {
            System.clearProperty("hackwars.localWebBaseUrl")
        }
    }

    private fun service(
        config: ComputerSessionConfig = ComputerSessionConfig(),
        auth: GameAuthDataService = FakeAuthDataService(),
        profile: GameProfileDataService = FakeProfileDataService(),
        telemetry: GameTelemetryDataService = FakeTelemetryDataService(),
        xmlRpcGateway: XmlRpcGateway = RecordingXmlRpcGateway(),
        passwordSource: PasswordSource = StaticPasswordSource(null),
        captchaImageSource: CaptchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
        captchaKeyGenerator: CaptchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
    ): ComputerSessionService {
        return ComputerSessionService(
            config = config,
            authDataService = auth,
            profileDataService = profile,
            telemetryDataService = telemetry,
            xmlRpcGateway = xmlRpcGateway,
            passwordSource = passwordSource,
            captchaImageSource = captchaImageSource,
            captchaKeyGenerator = captchaKeyGenerator,
        )
    }
}

private data class AuthenticateCall(
    val username: String,
    val loginPassword: String,
    val passwordIniValue: String?,
)

private data class PlayWindow(
    val ip: String,
    val startTime: Long,
    val endTime: Long,
)

private class FakeAuthDataService(
    var authenticateResult: Boolean = true,
    var forumLoginSnapshot: ForumLoginSnapshot? = ForumLoginSnapshot(
        ip = "10.0.0.1",
        npc = "Y",
        daysSinceLastLogin = 0,
    ),
) : GameAuthDataService {
    val authenticateCalls = mutableListOf<AuthenticateCall>()
    val markedForumLogins = mutableListOf<String>()
    val activityByIp = mutableMapOf<String, Pair<String, Int?>?>()

    override fun authenticate(username: String, loginPassword: String, passwordIniValue: String?): Boolean {
        authenticateCalls += AuthenticateCall(username, loginPassword, passwordIniValue)
        return authenticateResult
    }

    override fun findForumLoginSnapshotByName(username: String): ForumLoginSnapshot? {
        return forumLoginSnapshot
    }

    override fun findForumActivityByIp(ip: String): com.hackwars.data.model.ForumActivity? {
        val activity = activityByIp[ip] ?: return null
        return com.hackwars.data.model.ForumActivity(activity.first, activity.second)
    }

    override fun markForumLoginNow(username: String) {
        markedForumLogins += username
    }
}

private class FakeProfileDataService(
    val xmlByIp: MutableMap<String, String?> = mutableMapOf(),
) : GameProfileDataService {
    val readIps = mutableListOf<String>()
    val writes = mutableListOf<Pair<String, String>>()

    override fun findProfileXmlByIp(ip: String): String? {
        readIps += ip
        return xmlByIp[ip]
    }

    override fun upsertProfileXmlByIp(ip: String, xml: String) {
        writes += ip to xml
        xmlByIp[ip] = xml
    }
}

private class FakeTelemetryDataService : GameTelemetryDataService {
    val windows = mutableListOf<PlayWindow>()

    override fun recordPlayWindowByIp(ip: String, startTime: Long, endTime: Long) {
        windows += PlayWindow(ip, startTime, endTime)
    }
}

private class RecordingXmlRpcGateway(
    val response: Any? = null,
) : XmlRpcGateway {
    val requests = mutableListOf<Triple<String, String, Array<Any?>>>()

    override fun execute(url: String, method: String, params: Array<Any?>): Any? {
        requests.add(Triple(url, method, params))
        return response
    }
}

private class StaticPasswordSource(
    private val password: String?,
) : PasswordSource {
    override fun readPassword(): String? = password
}

private class StaticCaptchaImageSource(
    private val image: BufferedImage,
) : CaptchaImageSource {
    override fun load(url: String): BufferedImage = image
}

private class StaticCaptchaKeyGenerator(
    private val key: String,
) : CaptchaKeyGenerator {
    override fun generate(length: Int): String = key
}
