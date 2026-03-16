package com.hackwars.gui.login

import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicPasswordFieldUI

class LoginPasswordFieldUI : BasicPasswordFieldUI(),TextFieldUIBehavior by LoginTextFieldUIBehavior() {
    companion object {
        @JvmStatic
        fun createUI(c: JComponent) = LoginPasswordFieldUI()
    }

    override fun installDefaults() {
        super.installDefaults()

        (component as? JTextField)?.let { installFieldDefaults(it) }
    }

    override fun paintBackground(g: Graphics?) {
        (component as? JTextField)?.let { paintFieldBackground(it, g) }
    }
}