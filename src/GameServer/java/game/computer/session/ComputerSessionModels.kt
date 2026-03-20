package game.computer.session

data class ComputerSessionConfig(
    val localAuthFallbackEnabled: Boolean = true,
    val passwordFilePath: String = "password.ini",
    val captchaUrlPattern: String = "http://www.hackwars.net/securimage/securimage_show.php?id=%s",
    val pingTimeoutMillis: Long = 20000,
    val clientPacketTimeoutMillis: Long = 600000,
)

data class LoginRequest(
    val ip: String,
    val userName: String?,
    val loginPassword: String,
    val playFabAuthenticated: Boolean,
    val testing: Boolean,
)

data class LoginResult(
    val accepted: Boolean,
    val sendPreferences: Boolean,
)

data class CaptchaChallenge(
    val pixels: IntArray?,
    val key: String,
)

data class PlayStatisticsRequest(
    val loggedIn: Boolean,
    val ip: String,
    val currentTimeMillis: Long,
    val logInTimeMillis: Long,
    val lastPingTimeMillis: Long,
    val lastClientPacketTimeMillis: Long,
)

data class PlayStatisticsResult(
    val lastPingTimeMillis: Long,
    val logInTimeMillis: Long,
    val lastClientPacketTimeMillis: Long,
    val pingRecorded: Boolean,
    val clientPacketRecorded: Boolean,
)
