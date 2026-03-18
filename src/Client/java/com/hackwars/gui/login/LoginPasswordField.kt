package com.hackwars.gui.login

import javax.swing.JPasswordField

class LoginPasswordField : JPasswordField() {
    companion object {
        const val uiClassID: String = "LoginPasswordFieldUI"
    }

    override fun getUIClassID(): String {
        return LoginPasswordField.uiClassID
    }
}