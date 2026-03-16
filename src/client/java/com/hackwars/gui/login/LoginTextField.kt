package com.hackwars.gui.login

import javax.swing.JTextField

open class LoginTextField : JTextField() {
    companion object {
        const val uiClassID: String = "LoginTextFieldUI"
    }

    override fun getUIClassID(): String {
        return LoginTextField.uiClassID
    }
}