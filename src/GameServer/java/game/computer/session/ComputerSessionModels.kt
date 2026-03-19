package game.computer.session

data class ComputerSessionConfig(
    val remoteXmlRpcEnabled: Boolean = false,
    val localAuthFallbackEnabled: Boolean = true,
    val remoteFunctionPacksUrl: String = "http://www.hackwars.net/xmlrpc/functions.php",
    val remoteFunctionPacksMethod: String = "getFunctionPacks",
    val passwordFilePath: String = "password.ini",
    val captchaUrlPattern: String = "http://www.hackwars.net/securimage/securimage_show.php?id=%s",
    val authConnection: String = "127.0.0.1",
    val authDatabase: String = "hackwars_drupal",
    val authUsername: String = "root",
    val authPassword: String = "",
    val gameConnection: String = "127.0.0.1",
    val gameDatabase: String = "hackerforum",
    val gameUsername: String = "root",
    val gamePassword: String = "",
    val saveConnection: String = "127.0.0.1",
    val saveDatabase: String = "hackwars",
    val saveUsername: String = "root",
    val savePassword: String = "",
    val freeMaxOps: Int = 4096,
    val payMaxOps: Int = 16384,
    val freeFileSizeLimit: Int = 60000,
    val payFileSizeLimit: Int = 240000,
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

data class RemoteFunctionPackResult(
    val enabled: Boolean,
    val upgradedAccount: Boolean = false,
    val inactive: Boolean = false,
    val maxOps: Int,
    val fileSizeLimit: Int,
    val rawResult: Any? = null,
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
