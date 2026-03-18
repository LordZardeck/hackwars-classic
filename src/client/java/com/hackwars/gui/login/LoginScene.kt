package com.hackwars.gui.login

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

    override fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        super.onUsernamePasswordAuthenticate(email, password)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            Arrays.fill(password, '\u0000')
            onAuthenticationFailure("Username is required.")
            return
        }

        val passwordValue = String(password)
        Arrays.fill(password, '\u0000')
        thread(name = "playfab-login", isDaemon = true) {
            val authResult = authenticateWithPlayFab(trimmedEmail, passwordValue)
            SwingUtilities.invokeLater {
                if (authResult.token != null) {
                    userToken = authResult.token
                    Logger.info("PlayFab authentication successful for '{}': {}", trimmedEmail, authResult.token)
                } else {
                    onAuthenticationFailure(authResult.error ?: "PlayFab authentication failed.")
                }
            }
        }
    }

    override fun onAuthenticationFailure(reason: String) {
        Logger.warn("PlayFab authentication failure: {}", reason)
        super.onAuthenticationFailure(reason)
    }

    private data class AuthenticationResult(val token: String? = null, val error: String? = null)

    private fun authenticateWithPlayFab(email: String, password: String): AuthenticationResult {
        return try {
            val responseBody = sendPlayFabLoginRequest(email, password)
            val token = extractJsonString(responseBody, "SessionTicket")
            if (!token.isNullOrBlank()) {
                AuthenticationResult(token = token)
            } else {
                val errorMessage = extractJsonString(responseBody, "errorMessage")
                    ?: "PlayFab login failed: no session ticket was returned."
                AuthenticationResult(error = errorMessage)
            }
        } catch (t: Throwable) {
            AuthenticationResult(error = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun sendPlayFabLoginRequest(email: String, password: String): String {
        val baseUrl = System.getProperty(PLAYFAB_BASE_URL_PROPERTY, "https://$PLAYFAB_TITLE_ID.playfabapi.com")
            .trimEnd('/')
        val timeoutSeconds = System.getProperty(PLAYFAB_TIMEOUT_SECONDS_PROPERTY, DEFAULT_TIMEOUT_SECONDS.toString())
            .toLongOrNull()
            ?: DEFAULT_TIMEOUT_SECONDS

        val requestBody = """
            {"TitleId":"${escapeJson(PLAYFAB_TITLE_ID)}","Email":"${escapeJson(email)}","Password":"${escapeJson(password)}"}
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/Client/LoginWithEmailAddress"))
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            val errorMessage = extractJsonString(response.body(), "errorMessage")
                ?: "PlayFab returned HTTP ${response.statusCode()}."
            throw IllegalStateException(errorMessage)
        }
        return response.body()
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
}
