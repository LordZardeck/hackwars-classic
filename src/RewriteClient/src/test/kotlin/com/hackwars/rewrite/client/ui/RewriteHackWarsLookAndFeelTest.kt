package com.hackwars.rewrite.client.ui

import com.hackwars.rewrite.client.login.LoginPasswordField
import com.hackwars.rewrite.client.login.LoginPasswordFieldUI
import com.hackwars.rewrite.client.login.LoginTextField
import com.hackwars.rewrite.client.login.LoginTextFieldUI
import com.hackwars.rewrite.client.login.svgResource
import com.hackwars.rewrite.client.shell.shellImageIcon
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RewriteHackWarsLookAndFeelTest {
    @Test
    fun installsRewriteHackWarsLookAndFeelAndRegistersLoginDelegates() {
        val previous = UIManager.getLookAndFeel()

        try {
            RewriteUiBootstrap.installHackWarsLookAndFeel()

            assertIs<RewriteHackWarsLookAndFeel>(UIManager.getLookAndFeel())
            assertEquals(LoginTextFieldUI::class.java.name, UIManager.get(LoginTextField.uiClassID))
            assertEquals(LoginPasswordFieldUI::class.java.name, UIManager.get(LoginPasswordField.uiClassID))
        } finally {
            if (previous != null) {
                runCatching { UIManager.setLookAndFeel(previous) }
            }
        }
    }

    @Test
    fun parityAssetPackExposesRepresentativeRasterAndSvgAssets() {
        assertNotNull(hackWarsImageIcon("images/legacy/header.png"))
        assertNotNull(shellImageIcon("bank.png"))
        assertNotNull(svgResource("images/loading.svg"))
    }
}
