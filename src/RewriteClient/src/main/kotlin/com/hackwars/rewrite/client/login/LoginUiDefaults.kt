package com.hackwars.rewrite.client.login

import javax.swing.UIManager

object LoginUiDefaults {
    fun install() {
        UIManager.put(LoginTextField.uiClassID, LoginTextFieldUI::class.java.name)
        UIManager.put(LoginPasswordField.uiClassID, LoginPasswordFieldUI::class.java.name)
    }
}
