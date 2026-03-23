package game.computer.session

import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import com.hackwars.data.service.GameTelemetryDataService
import com.hackwars.data.model.PersistedProfileSave
import game.data.GameServerDataLocator

class ComputerSessionService(
    private val config: ComputerSessionConfig = ComputerSessionConfig(),
    private val authDataService: GameAuthDataService? = null,
    private val profileDataService: GameProfileDataService? = null,
    private val telemetryDataService: GameTelemetryDataService? = null,
    private val passwordSource: PasswordSource = FilePasswordSource(config.passwordFilePath),
    private val captchaImageSource: CaptchaImageSource = DefaultCaptchaImageSource(),
    private val captchaKeyGenerator: CaptchaKeyGenerator = RandomCaptchaKeyGenerator(),
) {
    private fun authDataService(): GameAuthDataService = authDataService ?: GameServerDataLocator.authService()

    private fun profileDataService(): GameProfileDataService = profileDataService ?: GameServerDataLocator.profileService()

    private fun telemetryDataService(): GameTelemetryDataService = telemetryDataService ?: GameServerDataLocator.telemetryService()

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

        try {
            val userName = request.userName
            if (userName.isNullOrBlank()) {
                return LoginResult(accepted = false, sendPreferences = false)
            }
            val loginPassword = passwordSource.readPassword()
            val authService = authDataService()
            val authenticated = authService.authenticate(userName, request.loginPassword, loginPassword)
            if (!authenticated && !request.testing) {
                return LoginResult(accepted = false, sendPreferences = false)
            }

            authService.markForumLoginNow(userName)
            val snapshot = authService.findForumLoginSnapshotByName(userName)
                ?: return LoginResult(accepted = false, sendPreferences = false)
            if (request.ip != snapshot.ip) {
                return LoginResult(accepted = false, sendPreferences = false)
            }

            return LoginResult(accepted = true, sendPreferences = true)
        } catch (_: Exception) {
            if (config.localAuthFallbackEnabled) {
                if (!request.userName.isNullOrBlank() && request.ip.isNotBlank()) {
                    return LoginResult(accepted = true, sendPreferences = true)
                }
            }
            return LoginResult(accepted = false, sendPreferences = false)
        }
    }

    fun loadLocalSaveXml(ip: String, active: Boolean): String {
        val save = loadLocalSave(ip, active)
        val xml = save.legacyXml
        if (!xml.isNullOrBlank()) {
            return xml
        }
        throw Exception("Unable to load local account data for ip=$ip. No legacy XML save payload found.")
    }

    fun loadLocalSave(ip: String, active: Boolean): PersistedProfileSave {
        ComputerSessionOverrides.localSave(ip, active)?.let { return it }

        try {
            val save = profileDataService().findPersistedProfileByIp(ip)
            if (save != null) {
                return save
            }
        } catch (e: Exception) {
            throw Exception("Unable to load local account data for ip=$ip from the database: ${e.message}", e)
        }

        throw Exception("Unable to load local account data for ip=$ip. No user.stats row found.")
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
        try {
            telemetryDataService().recordPlayWindowByIp(ip, startTime, endTime)
        } catch (_: Exception) {
        }
    }

    private fun recordPlayStatWindow(ip: String, startTime: Long, endTime: Long) {
        recordPlayWindow(ip, startTime, endTime)
    }
}
