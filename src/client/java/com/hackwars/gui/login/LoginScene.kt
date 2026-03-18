package com.hackwars.gui.login

import com.playfab.PlayFabClientAPI
import com.playfab.PlayFabClientModels
import com.playfab.PlayFabSettings
import org.slf4j.LoggerFactory
import java.util.Arrays
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class LoginScene : LoginSceneView() {
    companion object {
        private val Logger = LoggerFactory.getLogger(LoginScene::class.java)
        private val PLAYFAB_TITLE_ID = System.getProperty("hackwars.playfab.titleId", "1EAAB9")

        init {
            PlayFabSettings.TitleId = PLAYFAB_TITLE_ID
        }
    }

    var onPlayFabAuthenticated: ((playFabId: String, sessionTicket: String) -> Unit)? = null

    override fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        super.onUsernamePasswordAuthenticate(email, password)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            Arrays.fill(password, '\u0000')
            onAuthenticationFailure("Username is required.")
            return
        }

        thread(name = "playfab-login", isDaemon = true) {
            val authResult = authenticateWithPlayFab(trimmedEmail, password)
            SwingUtilities.invokeLater {
                if (authResult.token != null && authResult.playFabId != null) {
                    Logger.info("PlayFab authentication successful for '{}'.", trimmedEmail)
                    onPlayFabAuthenticated?.invoke(authResult.playFabId, authResult.token)
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

    fun onServerAuthenticationFailure(reason: String) {
        onAuthenticationFailure(reason)
    }

    private data class AuthenticationResult(
        val token: String? = null,
        val playFabId: String? = null,
        val error: String? = null,
    )

    private fun authenticateWithPlayFab(email: String, password: CharArray): AuthenticationResult {
        val passwordString = String(password)
        Arrays.fill(password, '\u0000')
        return try {
            val request = PlayFabClientModels.LoginWithEmailAddressRequest().apply {
                TitleId = PLAYFAB_TITLE_ID
                Email = email
                Password = passwordString
            }
            val result = PlayFabClientAPI.LoginWithEmailAddress(request)
            request.Password = null
            val login = result?.Result
            val token = login?.SessionTicket
            val playFabId = login?.PlayFabId
            if (!token.isNullOrBlank() && !playFabId.isNullOrBlank()) {
                AuthenticationResult(token = token, playFabId = playFabId)
            } else {
                val errorMessage = result?.Error?.errorMessage
                    ?: "PlayFab login failed: required auth values were not returned."
                AuthenticationResult(error = errorMessage)
            }
        } catch (t: Throwable) {
            AuthenticationResult(error = t.message ?: t.javaClass.simpleName)
        }
    }
}
