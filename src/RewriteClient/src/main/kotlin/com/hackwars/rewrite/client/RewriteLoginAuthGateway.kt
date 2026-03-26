package com.hackwars.rewrite.client

import com.playfab.PlayFabClientAPI
import com.playfab.PlayFabClientModels
import com.playfab.PlayFabSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RewriteLoginAuthResult(
    val playFabId: String? = null,
    val sessionTicket: String? = null,
    val error: String? = null,
) {
    val isSuccessful: Boolean
        get() = !playFabId.isNullOrBlank() && !sessionTicket.isNullOrBlank()

    companion object {
        fun success(playFabId: String, sessionTicket: String): RewriteLoginAuthResult {
            return RewriteLoginAuthResult(
                playFabId = playFabId,
                sessionTicket = sessionTicket,
            )
        }

        fun failure(error: String): RewriteLoginAuthResult {
            return RewriteLoginAuthResult(error = error)
        }
    }
}

interface RewriteLoginAuthGateway {
    suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult
}

class PlayFabRewriteLoginAuthGateway : RewriteLoginAuthGateway {
    companion object {
        private val playFabTitleId: String = System.getProperty("hackwars.playfab.titleId", "1EAAB9")

        init {
            PlayFabSettings.TitleId = playFabTitleId
        }
    }

    override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
        return withContext(Dispatchers.IO) {
            val passwordString = String(password)
            try {
                val request = PlayFabClientModels.LoginWithEmailAddressRequest().apply {
                    TitleId = playFabTitleId
                    Email = email
                    Password = passwordString
                }
                val response = PlayFabClientAPI.LoginWithEmailAddress(request)
                request.Password = null
                val login = response?.Result
                val sessionTicket = login?.SessionTicket
                val playFabId = login?.PlayFabId
                if (!sessionTicket.isNullOrBlank() && !playFabId.isNullOrBlank()) {
                    RewriteLoginAuthResult.success(playFabId = playFabId, sessionTicket = sessionTicket)
                } else {
                    RewriteLoginAuthResult.failure(
                        response?.Error?.errorMessage
                            ?: "PlayFab login failed: required auth values were not returned.",
                    )
                }
            } catch (throwable: Throwable) {
                RewriteLoginAuthResult.failure(throwable.message ?: throwable.javaClass.simpleName)
            } finally {
                password.fill('\u0000')
            }
        }
    }
}

data class DeterministicRewriteLoginAccount(
    val email: String,
    val password: String,
    val playFabId: String = email,
    val sessionTicket: String = "session-$email",
)

class DeterministicRewriteLoginAuthGateway(
    private val accounts: Map<String, DeterministicRewriteLoginAccount> = defaultAccounts(),
) : RewriteLoginAuthGateway {
    override suspend fun authenticate(email: String, password: CharArray): RewriteLoginAuthResult {
        val normalizedEmail = email.trim()
        val account = accounts[normalizedEmail]
            ?: return RewriteLoginAuthResult.failure("Unknown test account: $normalizedEmail").also {
                password.fill('\u0000')
            }

        val passwordString = String(password)
        return try {
            if (account.password == passwordString) {
                RewriteLoginAuthResult.success(
                    playFabId = account.playFabId,
                    sessionTicket = account.sessionTicket,
                )
            } else {
                RewriteLoginAuthResult.failure("Invalid credentials for $normalizedEmail")
            }
        } finally {
            password.fill('\u0000')
        }
    }

    companion object {
        fun defaultAccounts(): Map<String, DeterministicRewriteLoginAccount> {
            val account = DeterministicRewriteLoginAccount(
                email = "localuser",
                password = "password1234",
                playFabId = "PF-LOCALUSER",
                sessionTicket = "SESSION-LOCALUSER",
            )
            return mapOf(account.email to account)
        }
    }
}
