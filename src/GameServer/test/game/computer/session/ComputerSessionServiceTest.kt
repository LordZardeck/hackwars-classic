package game.computer.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
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
        val service = ComputerSessionService(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            sqlSessionFactory = RecordingSqlSessionFactory(),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource("secret"),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
        )

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
    }

    @Test
    fun `xor crypt round trips data with the same key`() {
        val service = ComputerSessionService(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            sqlSessionFactory = RecordingSqlSessionFactory(),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
        )

        val original = "hello".toByteArray()
        val encrypted = original.copyOf()
        val crypted = service.xorCrypt(encrypted, "key")
        val decrypted = service.xorCrypt(crypted.toByteArray(), "key")

        assertEquals("hello", decrypted)
    }

    @Test
    fun `local auth fallback short circuits when enabled`() {
        val service = ComputerSessionService(
            config = ComputerSessionConfig(localAuthFallbackEnabled = true),
            sqlSessionFactory = RecordingSqlSessionFactory(),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
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
    }

    @Test
    fun `password ini shortcut selects name only query and validates ip`() {
        val auth = RecordingSqlSession(
            queryResults = mutableMapOf(
                """SELECT name FROM hackerforum.users WHERE name = "player"""" to listOf("player"),
            )
        )
        val game = RecordingSqlSession(
            queryResults = mutableMapOf(
                """SELECT ip, npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in) FROM users WHERE name = "player"""" to listOf("10.0.0.1", "0", "0"),
            )
        )
        val factory = RecordingSqlSessionFactory(auth, game)
        val service = ComputerSessionService(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            sqlSessionFactory = factory,
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource("secret"),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
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
            listOf("""SELECT name FROM hackerforum.users WHERE name = "player""""),
            auth.queries
        )
    }

    @Test
    fun `wrong ip rejects successful db auth`() {
        val auth = RecordingSqlSession(
            queryResults = mutableMapOf(
                """SELECT name FROM users WHERE name = "player" AND pass = PASSWORD("pw")""" to listOf("player"),
            )
        )
        val game = RecordingSqlSession(
            queryResults = mutableMapOf(
                """SELECT ip, npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in) FROM users WHERE name = "player"""" to listOf("10.0.0.2", "0", "0"),
            )
        )
        val factory = RecordingSqlSessionFactory(auth, game)
        val service = ComputerSessionService(
            config = ComputerSessionConfig(localAuthFallbackEnabled = false),
            sqlSessionFactory = factory,
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource("different"),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
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
    }

    @Test
    fun `remote function packs expands upgrade limits`() {
        val gateway = RecordingXmlRpcGateway(
            response = arrayOf("ok", "ignored", true, false)
        )
        val service = ComputerSessionService(
            config = ComputerSessionConfig(remoteXmlRpcEnabled = true),
            sqlSessionFactory = RecordingSqlSessionFactory(),
            xmlRpcGateway = gateway,
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
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
        val service = ComputerSessionService(
            config = ComputerSessionConfig(),
            sqlSessionFactory = RecordingSqlSessionFactory(),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(image),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("67890"),
        )

        val result = service.generateCaptcha()

        assertEquals("67890", result.key)
        assertEquals(175 * 45, result.pixels?.size)
    }

    @Test
    fun `ping timeout records play statistics and resets timestamps`() {
        val session = RecordingSqlSession(
            queryResults = mutableMapOf(
                "SELECT uid FROM users WHERE ip = '10.0.0.1'" to listOf("42"),
            )
        )
        val service = ComputerSessionService(
            config = ComputerSessionConfig(),
            sqlSessionFactory = RecordingSqlSessionFactory(session),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
        )

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
        assertEquals(
            listOf("SELECT uid FROM users WHERE ip = '10.0.0.1'", "INSERT INTO hackwars.user_play_statistics VALUES ('42','1000','1000')"),
            session.queries
        )
    }

    @Test
    fun `client packet timeout records play statistics and resets timestamps`() {
        val session = RecordingSqlSession(
            queryResults = mutableMapOf(
                "SELECT uid FROM users WHERE ip = '10.0.0.1'" to listOf("42"),
            )
        )
        val service = ComputerSessionService(
            config = ComputerSessionConfig(),
            sqlSessionFactory = RecordingSqlSessionFactory(session),
            xmlRpcGateway = RecordingXmlRpcGateway(),
            passwordSource = StaticPasswordSource(null),
            captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
            captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
        )

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
    }

    @Test
    fun `load local save xml falls back to the database when the endpoint returns an error`() {
        val saveXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <save><ip>10.0.0.1</ip></save>
        """.trimIndent()
        val session = RecordingSqlSession(
            queryResults = mutableMapOf(
                "select stats from user where ip = '10.0.0.1' limit 1" to listOf(saveXml),
            )
        )

        try {
            System.setProperty(
                "hackwars.localWebBaseUrl",
                "http://127.0.0.1:1/hackwars",
            )

            val service = ComputerSessionService(
                config = ComputerSessionConfig(localAuthFallbackEnabled = false),
                sqlSessionFactory = RecordingSqlSessionFactory(session),
                xmlRpcGateway = RecordingXmlRpcGateway(),
                passwordSource = StaticPasswordSource(null),
                captchaImageSource = StaticCaptchaImageSource(BufferedImage(175, 45, BufferedImage.TYPE_INT_ARGB)),
                captchaKeyGenerator = StaticCaptchaKeyGenerator("12345"),
            )

            val xml = service.loadLocalSaveXml("10.0.0.1", active = false)

            assertTrue(xml.contains("<ip>10.0.0.1</ip>"))
            assertEquals(
                listOf("select stats from user where ip = '10.0.0.1' limit 1"),
                session.queries
            )
        } finally {
            System.clearProperty("hackwars.localWebBaseUrl")
        }
    }
}

private class RecordingSqlSessionFactory(
    private val first: RecordingSqlSession = RecordingSqlSession(),
    private val second: RecordingSqlSession = RecordingSqlSession(),
) : SqlSessionFactory {
    private var openCount = 0

    override fun open(connection: String, database: String, username: String, password: String): SqlSession {
        val session = if (openCount++ == 0) first else second
        return session
    }
}

private class RecordingSqlSession(
    val queryResults: MutableMap<String, List<String>?> = mutableMapOf(),
) : SqlSession {
    val queries = mutableListOf<String>()

    override fun query(command: String): List<String>? {
        queries.add(command)
        return queryResults[command]
    }

    override fun update(command: String) {
        queries.add(command)
    }

    override fun close() {
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
