package com.hackwars.gui.login

import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicTextFieldUI

open class LoginTextFieldUI : BasicTextFieldUI(), TextFieldUIBehavior by LoginTextFieldUIBehavior() {
    companion object {
        @JvmStatic
        fun createUI(c: JComponent) = LoginTextFieldUI()
    }

    override fun installDefaults() {
        super.installDefaults()

        (component as? JTextField)?.let { installFieldDefaults(it) }
    }

    override fun paintSafely(g: Graphics) {
        (component as? JTextField)?.let { paintFieldBackground(it, g) }
        super.paintSafely(g)
    }
}
