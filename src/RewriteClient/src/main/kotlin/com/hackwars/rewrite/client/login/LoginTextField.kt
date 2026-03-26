package com.hackwars.rewrite.client.login

import javax.swing.JTextField

open class LoginTextField : JTextField() {
    companion object {
        const val uiClassID: String = "LoginTextFieldUI"

        init {
            LoginUiDefaults.install()
        }
    }

    override fun getUIClassID(): String = LoginTextField.uiClassID
}
