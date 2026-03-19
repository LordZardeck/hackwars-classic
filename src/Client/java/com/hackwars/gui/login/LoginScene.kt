package com.hackwars.gui.login

import com.hackwars.client.ClientAuthGateway
import com.hackwars.client.ClientAuthResult
import com.hackwars.client.PlayFabClientAuthGateway
import org.slf4j.LoggerFactory
import java.util.Arrays
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class LoginScene(
    private val authGateway: ClientAuthGateway = PlayFabClientAuthGateway(),
) : LoginSceneView() {
    companion object {
        private val Logger = LoggerFactory.getLogger(LoginScene::class.java)
    }

    var onPlayFabAuthenticated: ((playFabId: String, sessionTicket: String) -> Unit)? = null

    fun submitCredentials(email: String, password: CharArray) {
        onUsernamePasswordAuthenticate(email, password)
    }

    override fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        super.onUsernamePasswordAuthenticate(email, password)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            Arrays.fill(password, '\u0000')
            onAuthenticationFailure("Username is required.")
            return
        }

        val passwordSnapshot = password.copyOf()
        Arrays.fill(password, '\u0000')
        thread(name = "client-auth-login", isDaemon = true) {
            val authResult = try {
                runCatching {
                    authGateway.authenticate(trimmedEmail, passwordSnapshot)
                }.getOrElse { throwable ->
                    ClientAuthResult.failure(throwable.message ?: throwable.javaClass.simpleName)
                }
            } finally {
                Arrays.fill(passwordSnapshot, '\u0000')
            }
            SwingUtilities.invokeLater {
                if (authResult.isSuccessful) {
                    Logger.info("Authentication successful for '{}'.", trimmedEmail)
                    onPlayFabAuthenticated?.invoke(authResult.playFabId!!, authResult.sessionTicket!!)
                } else {
                    onAuthenticationFailure(authResult.error ?: "Authentication failed.")
                }
            }
        }
    }

    override fun onAuthenticationFailure(reason: String) {
        Logger.warn("Authentication failure: {}", reason)
        super.onAuthenticationFailure(reason)
    }

    fun onServerAuthenticationFailure(reason: String) {
        onAuthenticationFailure(reason)
    }
}
