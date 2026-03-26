package com.hackwars.rewrite.client.login

class LoginScene : LoginSceneView() {
    var onSubmitCredentials: ((email: String, password: CharArray) -> Unit)? = null

    fun submitCredentials(email: String, password: CharArray) {
        onUsernamePasswordAuthenticate(email, password)
    }

    override fun onUsernamePasswordAuthenticate(email: String, password: CharArray) {
        super.onUsernamePasswordAuthenticate(email, password)
        onSubmitCredentials?.invoke(email, password)
    }

    fun onServerAuthenticationFailure(reason: String) {
        onAuthenticationFailure(reason)
    }
}
