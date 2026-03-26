package com.hackwars.rewrite.client.login

import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicPasswordFieldUI

class LoginPasswordFieldUI : BasicPasswordFieldUI(), TextFieldUIBehavior by LoginTextFieldUIBehavior() {
    companion object {
        @JvmStatic
        fun createUI(component: JComponent): LoginPasswordFieldUI = LoginPasswordFieldUI()
    }

    override fun installDefaults() {
        super.installDefaults()
        (component as? JTextField)?.let(::installFieldDefaults)
    }

    override fun paintSafely(graphics: Graphics) {
        (component as? JTextField)?.let { paintFieldBackground(it, graphics) }
        super.paintSafely(graphics)
    }
}
