package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteDesktopShellView
import com.hackwars.rewrite.client.shell.RewriteShellChromeController
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.client.shell.RewriteShellWindowHost
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientRuntimeState
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RewriteShellChromeControllerTest {
    @Test
    fun bindsMenuCommandsAndDetachesOnClose() {
        val launchedCommands = mutableListOf<RewriteShellCommand>()
        val shellHost = RewriteDesktopShellView()
        var attachedHost: RewriteShellWindowHost? = null

        val controller = RewriteShellChromeController(
            shellHost = shellHost,
            launchShellCommand = launchedCommands::add,
            attachShellWindowHost = { attachedHost = it },
            updateFrameTitle = {},
        )
        try {
            flushEdt()
            assertSame(shellHost, attachedHost)

            SwingUtilities.invokeAndWait {
                shellHost.menuBar.commandItem(RewriteShellCommand.HOME)?.doClick()
            }
            assertEquals(listOf(RewriteShellCommand.HOME), launchedCommands)

            controller.close()
            flushEdt()
            assertEquals(null, attachedHost)

            SwingUtilities.invokeAndWait {
                shellHost.menuBar.commandItem(RewriteShellCommand.NETWORK)?.doClick()
            }
            assertEquals(listOf(RewriteShellCommand.HOME), launchedCommands)
        } finally {
            controller.close()
        }
    }

    @Test
    fun rendersDesktopChromeFromExplicitUpdatesAndRoute() {
        val shellHost = RewriteDesktopShellView()
        val frameTitles = mutableListOf<String>()

        val controller = RewriteShellChromeController(
            shellHost = shellHost,
            launchShellCommand = {},
            attachShellWindowHost = {},
            updateFrameTitle = frameTitles::add,
        )
        try {
            controller.updateRoute(RewriteClientRoute.DESKTOP)
            controller.updateAcceptedPlayerIp("192.0.2.10")
            controller.updateShellState(
                ClientGameSnapshot(
                    identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                    runtime = ClientRuntimeState(countdownSeconds = 0),
                ),
            )
            flushEdt()

            assertTrue(shellHost.statsRail.isVisible)
            assertEquals("Hack Wars - 192.0.2.10", frameTitles.last())
        } finally {
            controller.close()
        }
    }

    private fun flushEdt() {
        SwingUtilities.invokeAndWait {}
    }
}
