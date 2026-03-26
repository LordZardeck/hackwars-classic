package com.hackwars.rewrite.client.login

import javax.swing.JPasswordField

class LoginPasswordField : JPasswordField() {
    companion object {
        const val uiClassID: String = "LoginPasswordFieldUI"

        init {
            LoginUiDefaults.install()
        }
    }

    override fun getUIClassID(): String = LoginPasswordField.uiClassID
}
