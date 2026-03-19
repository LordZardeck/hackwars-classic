package com.hackwars.client

import com.playfab.PlayFabClientAPI
import com.playfab.PlayFabClientModels
import com.playfab.PlayFabSettings

data class ClientAuthResult(
    val playFabId: String? = null,
    val sessionTicket: String? = null,
    val error: String? = null,
) {
    val isSuccessful: Boolean
        get() = !playFabId.isNullOrBlank() && !sessionTicket.isNullOrBlank()

    companion object {
        fun success(playFabId: String, sessionTicket: String): ClientAuthResult {
            return ClientAuthResult(playFabId = playFabId, sessionTicket = sessionTicket)
        }

        fun failure(error: String): ClientAuthResult {
            return ClientAuthResult(error = error)
        }
    }
}

interface ClientAuthGateway {
    fun authenticate(email: String, password: CharArray): ClientAuthResult
}

class PlayFabClientAuthGateway : ClientAuthGateway {
    companion object {
        private val PLAYFAB_TITLE_ID = System.getProperty("hackwars.playfab.titleId", "1EAAB9")

        init {
            PlayFabSettings.TitleId = PLAYFAB_TITLE_ID
        }
    }

    override fun authenticate(email: String, password: CharArray): ClientAuthResult {
        val passwordString = String(password)
        try {
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
            return if (!token.isNullOrBlank() && !playFabId.isNullOrBlank()) {
                ClientAuthResult.success(playFabId, token)
            } else {
                val errorMessage = result?.Error?.errorMessage
                    ?: "PlayFab login failed: required auth values were not returned."
                ClientAuthResult.failure(errorMessage)
            }
        } catch (t: Throwable) {
            return ClientAuthResult.failure(t.message ?: t.javaClass.simpleName)
        } finally {
            password.fill('\u0000')
        }
    }
}

data class DeterministicClientAuthAccount(
    val email: String,
    val password: String,
    val playFabId: String = email,
    val sessionTicket: String = "session-$email",
)

class DeterministicClientAuthGateway(
    private val accounts: Map<String, DeterministicClientAuthAccount> = defaultAccounts(),
) : ClientAuthGateway {
    override fun authenticate(email: String, password: CharArray): ClientAuthResult {
        val normalizedEmail = email.trim()
        val account = accounts[normalizedEmail]
            ?: return ClientAuthResult.failure("Unknown test account: $normalizedEmail")

        val passwordString = String(password)
        return try {
            if (account.password == passwordString) {
                ClientAuthResult.success(account.playFabId, account.sessionTicket)
            } else {
                ClientAuthResult.failure("Invalid credentials for $normalizedEmail")
            }
        } finally {
            password.fill('\u0000')
        }
    }

    companion object {
        fun defaultAccounts(): Map<String, DeterministicClientAuthAccount> {
            val account = DeterministicClientAuthAccount(
                email = "localuser",
                password = "password1234",
                playFabId = "PF-LOCALUSER",
                sessionTicket = "SESSION-LOCALUSER",
            )
            return mapOf(account.email to account)
        }
    }
}
