package com.hackwars.gui.login

import com.playfab.PlayFabClientModels
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class LoginScene : LoginSceneView() {
    companion object {
        private val Logger = LoggerFactory.getLogger(LoginScene::class.java)
        private const val PLAYFAB_TITLE_ID_PROPERTY = "hackwars.playfab.titleId"
        private val PLAYFAB_TITLE_ID = System.getProperty(PLAYFAB_TITLE_ID_PROPERTY, "1EAAB9")
        private const val PLAYFAB_BASE_URL_PROPERTY = "hackwars.playfab.baseUrl"
        private const val PLAYFAB_TIMEOUT_SECONDS_PROPERTY = "hackwars.playfab.timeoutSeconds"
        private const val DEFAULT_TIMEOUT_SECONDS = 12L
    }

    @Volatile
    private var userToken: String? = null
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    var onPlayFabAuthenticated: ((playFabId: String, sessionTicket: String) -> Unit)? = null

    override fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        super.onUsernamePasswordAuthenticate(email, password)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            Arrays.fill(password, '\u0000')
            onAuthenticationFailure("Username is required.")
            return
        }

        val escapedPasswordBytes = encodeEscapedJsonPasswordBytes(password)
        Arrays.fill(password, '\u0000')
        thread(name = "playfab-login", isDaemon = true) {
            try {
                val authResult = authenticateWithPlayFab(trimmedEmail, escapedPasswordBytes)
                SwingUtilities.invokeLater {
                    if (authResult.token != null && authResult.playFabId != null) {
                        userToken = authResult.token
                        Logger.info("PlayFab authentication successful for '{}'.", trimmedEmail)
                        onPlayFabAuthenticated?.invoke(authResult.playFabId, authResult.token)
                    } else {
                        onAuthenticationFailure(authResult.error ?: "PlayFab authentication failed.")
                    }
                }
            } finally {
                Arrays.fill(escapedPasswordBytes, 0)
            }
        }
    }

    override fun onAuthenticationFailure(reason: String) {
        Logger.warn("PlayFab authentication failure: {}", reason)
        super.onAuthenticationFailure(reason)
    }

    fun onServerAuthenticationFailure(reason: String) {
        onAuthenticationFailure(reason)
    }

    private data class AuthenticationResult(
        val token: String? = null,
        val playFabId: String? = null,
        val error: String? = null,
    )

    private fun authenticateWithPlayFab(email: String, escapedPasswordBytes: ByteArray): AuthenticationResult {
        return try {
            val responseBody = sendPlayFabLoginRequest(email, escapedPasswordBytes)
            val token = extractJsonString(responseBody, "SessionTicket")
            val playFabId = extractJsonString(responseBody, "PlayFabId")
            if (!token.isNullOrBlank() && !playFabId.isNullOrBlank()) {
                AuthenticationResult(token = token, playFabId = playFabId)
            } else {
                val errorMessage = extractJsonString(responseBody, "errorMessage")
                    ?: "PlayFab login failed: required auth values were not returned."
                AuthenticationResult(error = errorMessage)
            }
        } catch (t: Throwable) {
            AuthenticationResult(error = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun sendPlayFabLoginRequest(email: String, escapedPasswordBytes: ByteArray): String {
        val baseUrl = System.getProperty(PLAYFAB_BASE_URL_PROPERTY, "https://$PLAYFAB_TITLE_ID.playfabapi.com")
            .trimEnd('/')
        val timeoutSeconds = System.getProperty(PLAYFAB_TIMEOUT_SECONDS_PROPERTY, DEFAULT_TIMEOUT_SECONDS.toString())
            .toLongOrNull()
            ?: DEFAULT_TIMEOUT_SECONDS

        val requestBodyPrefix = """{"TitleId":"${escapeJson(PLAYFAB_TITLE_ID)}","Email":"${escapeJson(email)}","Password":""""
            .toByteArray(StandardCharsets.UTF_8)
        val requestBodySuffix = "\"}".toByteArray(StandardCharsets.UTF_8)
        val requestBodyBytes = combineRequestBodyBytes(requestBodyPrefix, escapedPasswordBytes, requestBodySuffix)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/Client/LoginWithEmailAddress"))
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBodyBytes))
            .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                val errorMessage = extractJsonString(response.body(), "errorMessage")
                    ?: "PlayFab returned HTTP ${response.statusCode()}."
                throw IllegalStateException(errorMessage)
            }
            return response.body()
        } finally {
            Arrays.fill(requestBodyBytes, 0)
        }
    }

    private fun extractJsonString(json: String, field: String): String? {
        val regex = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val value = regex.find(json)?.groupValues?.getOrNull(1) ?: return null
        return value
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun encodeEscapedJsonPasswordBytes(password: CharArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val utf8 = ByteArray(encoded.remaining())
        encoded.get(utf8)

        val out = ByteArrayOutputStream(utf8.size + 8)
        for (b in utf8) {
            when (b.toInt() and 0xFF) {
                '\\'.code -> out.write('\\'.code).also { out.write('\\'.code) }
                '"'.code -> out.write('\\'.code).also { out.write('"'.code) }
                '\n'.code -> out.write('\\'.code).also { out.write('n'.code) }
                '\r'.code -> out.write('\\'.code).also { out.write('r'.code) }
                '\t'.code -> out.write('\\'.code).also { out.write('t'.code) }
                else -> out.write(b.toInt())
            }
        }
        Arrays.fill(utf8, 0)
        return out.toByteArray()
    }

    private fun combineRequestBodyBytes(prefix: ByteArray, password: ByteArray, suffix: ByteArray): ByteArray {
        val output = ByteArray(prefix.size + password.size + suffix.size)
        System.arraycopy(prefix, 0, output, 0, prefix.size)
        System.arraycopy(password, 0, output, prefix.size, password.size)
        System.arraycopy(suffix, 0, output, prefix.size + password.size, suffix.size)
        return output
    }
}
