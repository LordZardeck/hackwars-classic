package game.computer.session

import org.w3c.dom.Node
import util.LoadXML
import util.LocalWebConfig

class ComputerSessionService(
    private val config: ComputerSessionConfig = ComputerSessionConfig(),
    private val sqlSessionFactory: SqlSessionFactory = DefaultSqlSessionFactory(),
    private val xmlRpcGateway: XmlRpcGateway = DefaultXmlRpcGateway(),
    private val passwordSource: PasswordSource = FilePasswordSource(config.passwordFilePath),
    private val captchaImageSource: CaptchaImageSource = DefaultCaptchaImageSource(),
    private val captchaKeyGenerator: CaptchaKeyGenerator = RandomCaptchaKeyGenerator(),
) {
    fun xorCrypt(data: ByteArray, key: String): String {
        val keyBytes = key.toByteArray()
        if (keyBytes.isEmpty()) {
            return String(data)
        }
        for (index in data.indices) {
            data[index] = (data[index].toInt() xor keyBytes[index % keyBytes.size].toInt()).toByte()
        }
        return String(data)
    }

    fun authenticate(request: LoginRequest): LoginResult {
        if (request.playFabAuthenticated) {
            return LoginResult(accepted = true, sendPreferences = true)
        }

        if (config.localAuthFallbackEnabled) {
            if (!request.userName.isNullOrBlank() && request.ip.isNotBlank()) {
                return LoginResult(accepted = true, sendPreferences = true)
            }
        }

        var authSession: SqlSession? = null
        var gameSession: SqlSession? = null
        var accepted = false
        var ipCheck = ""

        try {
            authSession = sqlSessionFactory.open(
                config.authConnection,
                config.authDatabase,
                config.authUsername,
                config.authPassword,
            )

            val loginPassword = passwordSource.readPassword()
            val query = if (request.loginPassword != loginPassword) {
                "SELECT name FROM users WHERE name = \"${request.userName}\" AND pass = PASSWORD(\"${request.loginPassword}\")"
            } else {
                "SELECT name FROM hackerforum.users WHERE name = \"${request.userName}\""
            }

            val authResult = authSession.query(query)
            if ((authResult == null || authResult.isEmpty()) && !request.testing) {
                return LoginResult(accepted = false, sendPreferences = false)
            }

            gameSession = sqlSessionFactory.open(
                config.gameConnection,
                config.gameDatabase,
                config.gameUsername,
                config.gamePassword,
            )

            val lookupQuery = "SELECT ip, npc, TO_DAYS(NOW()) - TO_DAYS(last_logged_in) FROM users WHERE name = \"${request.userName}\""
            val result = gameSession.query(lookupQuery)
            gameSession.update("UPDATE users SET last_logged_in=NOW() WHERE name = \"${request.userName}\"")

            if (result != null && result.size > 0) {
                ipCheck = result[0]
                accepted = true
            }

            if (request.ip != ipCheck) {
                return LoginResult(accepted = false, sendPreferences = false)
            }

            return LoginResult(accepted = accepted, sendPreferences = accepted)
        } catch (_: Exception) {
            if (config.localAuthFallbackEnabled) {
                if (!request.userName.isNullOrBlank() && request.ip.isNotBlank()) {
                    return LoginResult(accepted = true, sendPreferences = true)
                }
            }
            return LoginResult(accepted = false, sendPreferences = false)
        } finally {
            try {
                authSession?.close()
            } catch (_: Exception) {
            }
            try {
                gameSession?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun loadLocalSaveXml(ip: String, active: Boolean): String {
        ComputerSessionOverrides.localSaveXml(ip, active)?.let { return it }

        val passwordSuffix = passwordSource.readPassword()?.let { "&pass=$it" } ?: ""
        val loadXml = LoadXML()
        val failures = StringBuilder()
        var lastError: Exception? = null

        for (url in localLoginUrls(ip, active, passwordSuffix)) {
            try {
                loadXml.loadURL(url)
                val xmlError = extractLoadXmlError(loadXml)
                if (xmlError == null) {
                    return loadXml.toString()
                }
                lastError = Exception("Local login endpoint returned error: $xmlError")
                failures.append("URL ").append(url).append(" returned error: ").append(xmlError).append(". ")
                if (active) {
                    break
                }
            } catch (e: Exception) {
                lastError = e
                failures.append("URL ").append(url).append(" failed: ").append(e.message).append(". ")
            }
        }

        var saveSession: SqlSession? = null
        try {
            saveSession = sqlSessionFactory.open(
                config.saveConnection,
                config.saveDatabase,
                config.saveUsername,
                config.savePassword,
            )
            val results = saveSession.query("select stats from user where ip = '$ip' limit 1")
            val xml = results?.firstOrNull()
            if (!xml.isNullOrBlank()) {
                return xml
            }
            failures.append("DB fallback failed: No user.stats row found for ip=$ip. ")
        } catch (e: Exception) {
            failures.append("DB fallback failed: ").append(e.message).append(". ")
            lastError = e
        } finally {
            try {
                saveSession?.close()
            } catch (_: Exception) {
            }
        }

        val detail = "Unable to load local account data for ip=$ip. $failures"
        throw Exception(detail, lastError)
    }

    fun requestFunctionPacks(ip: String): RemoteFunctionPackResult {
        if (!config.remoteXmlRpcEnabled) {
            return RemoteFunctionPackResult(
                enabled = false,
                maxOps = config.freeMaxOps,
                fileSizeLimit = config.freeFileSizeLimit,
            )
        }

        return try {
            val result = xmlRpcGateway.execute(
                config.remoteFunctionPacksUrl,
                config.remoteFunctionPacksMethod,
                arrayOf(ip as Any?),
            )
            if (result is Array<*> && result.size > 3) {
                val upgraded = result[2] as? Boolean ?: false
                val inactive = result[3] as? Boolean ?: false
                RemoteFunctionPackResult(
                    enabled = true,
                    upgradedAccount = upgraded,
                    inactive = inactive,
                    maxOps = if (upgraded) config.payMaxOps else config.freeMaxOps,
                    fileSizeLimit = if (upgraded) config.payFileSizeLimit else config.freeFileSizeLimit,
                    rawResult = result,
                )
            } else {
                RemoteFunctionPackResult(
                    enabled = true,
                    maxOps = config.freeMaxOps,
                    fileSizeLimit = config.freeFileSizeLimit,
                    rawResult = result,
                )
            }
        } catch (_: Exception) {
            RemoteFunctionPackResult(
                enabled = true,
                maxOps = config.freeMaxOps,
                fileSizeLimit = config.freeFileSizeLimit,
            )
        }
    }

    fun executeRemote(url: String, method: String, params: Array<Any?>): Any? {
        return xmlRpcGateway.execute(url, method, params)
    }

    fun generateCaptcha(): CaptchaChallenge {
        val key = captchaKeyGenerator.generate(5)
        return try {
            val image = captchaImageSource.load(config.captchaUrlPattern.format(key))
            CaptchaChallenge(
                pixels = image.getRGB(0, 0, 175, 45, null, 0, 175),
                key = key,
            )
        } catch (_: Exception) {
            CaptchaChallenge(pixels = null, key = key)
        }
    }

    fun recordPlayStatistics(request: PlayStatisticsRequest): PlayStatisticsResult {
        if (!request.loggedIn) {
            return PlayStatisticsResult(
                lastPingTimeMillis = request.lastPingTimeMillis,
                logInTimeMillis = request.logInTimeMillis,
                lastClientPacketTimeMillis = request.lastClientPacketTimeMillis,
                pingRecorded = false,
                clientPacketRecorded = false,
            )
        }

        var lastPingTime = request.lastPingTimeMillis
        var logInTime = request.logInTimeMillis
        var lastClientPacketTime = request.lastClientPacketTimeMillis
        var pingRecorded = false
        var clientPacketRecorded = false

        if (lastPingTime != 0L && request.currentTimeMillis - lastPingTime > config.pingTimeoutMillis) {
            recordPlayStatWindow(request.ip, logInTime, lastPingTime)
            lastPingTime = 0L
            logInTime = 0L
            pingRecorded = true
        }

        if (
            lastClientPacketTime != 0L &&
            logInTime != 0L &&
            request.currentTimeMillis - lastClientPacketTime > config.clientPacketTimeoutMillis
        ) {
            recordPlayStatWindow(request.ip, logInTime, lastClientPacketTime)
            lastClientPacketTime = 0L
            logInTime = 0L
            clientPacketRecorded = true
        }

        return PlayStatisticsResult(
            lastPingTimeMillis = lastPingTime,
            logInTimeMillis = logInTime,
            lastClientPacketTimeMillis = lastClientPacketTime,
            pingRecorded = pingRecorded,
            clientPacketRecorded = clientPacketRecorded,
        )
    }

    fun recordPlayWindow(ip: String, startTime: Long, endTime: Long) {
        recordPlayStatWindow(ip, startTime, endTime)
    }

    private fun recordPlayStatWindow(ip: String, startTime: Long, endTime: Long) {
        var session: SqlSession? = null
        try {
            session = sqlSessionFactory.open(
                config.authConnection,
                config.authDatabase,
                config.authUsername,
                config.authPassword,
            )
            val result = session.query("""SELECT uid FROM users WHERE ip = '$ip'""")
            val uid = result?.firstOrNull() ?: return
            session.update("""INSERT INTO hackwars.user_play_statistics VALUES ('$uid','$startTime','$endTime')""")
        } catch (_: Exception) {
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun localLoginUrls(ip: String, active: Boolean, addPass: String): List<String> {
        val suffix = buildString {
            append("/login.html?ip=").append(ip).append("&serverID=1")
            if (active) {
                append("&active=true")
            }
            append(addPass)
        }

        val base = LocalWebConfig.getBaseUrl()
        val withContext = base + suffix
        return if (base.endsWith("/hackwars")) {
            listOf(withContext)
        } else {
            listOf(withContext, "$base/hackwars$suffix")
        }
    }

    private fun extractLoadXmlError(loadXml: LoadXML): String? {
        return try {
            val errorNode = loadXml.findNodeRecursive("error", 0) ?: return null
            val messageNode = loadXml.findNodeRecursive(errorNode, "message", 0) ?: return "Unknown remote error."
            val textNode: Node = loadXml.findNodeRecursive(messageNode, "#text", 0) ?: return "Unknown remote error."
            textNode.nodeValue ?: "Unknown remote error."
        } catch (_: Exception) {
            "Unknown remote error."
        }
    }
}
