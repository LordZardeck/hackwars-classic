package com.hackwars.rewrite.client.ui

import com.hackwars.rewrite.client.login.LoginPasswordField
import com.hackwars.rewrite.client.login.LoginPasswordFieldUI
import com.hackwars.rewrite.client.login.LoginTextField
import com.hackwars.rewrite.client.login.LoginTextFieldUI
import javax.swing.UIDefaults
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

class RewriteHackWarsLookAndFeel : MetalLookAndFeel() {
    override fun getName(): String = "Rewrite HackWars"

    override fun getID(): String = "RewriteHackWars"

    override fun getDescription(): String = "Rewrite-owned HackWars cross-platform Metal look and feel."

    override fun initClassDefaults(table: UIDefaults) {
        super.initClassDefaults(table)
        table.putDefaults(
            arrayOf(
                LoginTextField.uiClassID, LoginTextFieldUI::class.java.name,
                LoginPasswordField.uiClassID, LoginPasswordFieldUI::class.java.name,
            ),
        )
    }
}

object RewriteUiBootstrap {
    fun installHackWarsLookAndFeel() {
        val current = UIManager.getLookAndFeel()
        if (current is RewriteHackWarsLookAndFeel) {
            return
        }
        runCatching {
            UIManager.setLookAndFeel(RewriteHackWarsLookAndFeel())
        }.onFailure {
            println("Warning: Unable to set rewrite HackWars look and feel: ${it.message}")
        }
    }
}
