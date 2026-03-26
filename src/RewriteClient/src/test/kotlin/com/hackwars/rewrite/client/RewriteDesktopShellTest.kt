package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteDesktopMenuBar
import com.hackwars.rewrite.client.shell.RewriteDesktopShellView
import com.hackwars.rewrite.client.shell.RewritePlaceholderInternalFrame
import com.hackwars.rewrite.client.shell.RewriteShellCommand
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewriteDesktopShellTest {
    @Test
    fun menuTaxonomyMatchesLegacyLabelsAndGrouping() {
        val menuBar = RewriteDesktopMenuBar()

        assertEquals(
            listOf("Applications", "Places", "System", "Tutorials"),
            menuBar.topLevelMenus().map { it.text },
        )

        val applicationsMenu = menuBar.topLevelMenus().first()
        val bankingMenu = applicationsMenu.getMenuComponent(0) as JMenu
        val internetMenu = applicationsMenu.getMenuComponent(1) as JMenu
        val hackingToolsMenu = applicationsMenu.getMenuComponent(2) as JMenu

        assertEquals("Banking", bankingMenu.text)
        assertEquals(listOf("Deposit", "Withdraw", "Transfer"), menuItemTexts(bankingMenu))
        assertEquals("Internet", internetMenu.text)
        assertEquals(listOf("Web Browser", "Store", "Site Editor"), menuItemTexts(internetMenu))
        assertEquals("Hacking Tools", hackingToolsMenu.text)
        assertEquals(listOf("Port Scan", "Attack Port", "Redirect Port", "Zombie Attack"), menuItemTexts(hackingToolsMenu))
        assertEquals(
            listOf("Script Editor", "Create Bounty"),
            listOf(
                applicationsMenu.getMenuComponent(3),
                applicationsMenu.getMenuComponent(4),
            ).map { component -> (component as JMenuItem).text },
        )
    }

    @Test
    fun launchingSameCommandRestoresExistingFrameInsteadOfDuplicatingIt() {
        val controller = RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = NoOpRewriteServiceSessionGateway,
        )
        val shellHost = RewriteDesktopShellView()
        controller.attachShellHost(shellHost)

        invokeAndWait {
            controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            val frame = shellHost.desktopPane.allFrames.single()
            frame.isIcon = true

            controller.launchShellCommand(RewriteShellCommand.DEPOSIT)

            assertEquals(1, shellHost.desktopPane.allFrames.size)
            assertEquals(frame, shellHost.desktopPane.allFrames.single())
        }

        controller.shutdown()
    }

    @Test
    fun launchingHomeCommandCreatesRealWindowAndRelaunchRestoresIt() {
        val controller = RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = NoOpRewriteServiceSessionGateway,
        )
        val shellHost = RewriteDesktopShellView()
        controller.attachShellHost(shellHost)

        invokeAndWait {
            controller.launchShellCommand(RewriteShellCommand.HOME)
            val frame = shellHost.desktopPane.allFrames.single()

            assertEquals("rewrite-home-window", frame.name)

            controller.launchShellCommand(RewriteShellCommand.HOME)

            assertEquals(1, shellHost.desktopPane.allFrames.size)
            assertEquals(frame, shellHost.desktopPane.allFrames.single())
        }

        controller.shutdown()
    }

    @Test
    fun minimizingAndRestoringFramesUpdatesTaskBar() {
        val shellHost = RewriteDesktopShellView()
        val controller = RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = NoOpRewriteServiceSessionGateway,
        )
        controller.attachShellHost(shellHost)

        invokeAndWait {
            val frames = mutableListOf<javax.swing.JInternalFrame>()
            RewriteShellCommand.entries
                .filterNot { command ->
                    command == RewriteShellCommand.CREATE_BOUNTY || command == RewriteShellCommand.ZOMBIE_ATTACK
                }
                .take(10)
                .forEach { command ->
                    controller.launchShellCommand(command)
                    val frame = shellHost.desktopPane.allFrames.single { it.title == command.title }
                    frames += frame
                    frame.isIcon = true
                }

            assertEquals(10, shellHost.menuBar.taskBar.minimizedApplicationCount())

            val firstFrame = frames.first()
            firstFrame.isIcon = false

            assertEquals(9, shellHost.menuBar.taskBar.minimizedApplicationCount())
        }
        controller.shutdown()
    }

    @Test
    fun controllerShutdownDisposesShellWindowsCleanly() {
        val controller = RewriteRootController(
            authGateway = DeterministicRewriteLoginAuthGateway(),
            sessionGateway = NoOpRewriteServiceSessionGateway,
        )
        val shellHost = RewriteDesktopShellView()
        controller.attachShellHost(shellHost)

        invokeAndWait {
            controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            controller.launchShellCommand(RewriteShellCommand.PORT_SCAN)
            assertEquals(2, shellHost.desktopPane.allFrames.size)
        }

        controller.shutdown()

        invokeAndWait {
            assertTrue(shellHost.desktopPane.allFrames.isEmpty())
            assertEquals(0, shellHost.menuBar.taskBar.minimizedApplicationCount())
        }
    }

    private fun menuItemTexts(menu: JMenu): List<String> {
        return (0 until menu.itemCount)
            .mapNotNull { menu.getItem(it)?.text }
    }

    private inline fun invokeAndWait(crossinline block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
            return
        }
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            runCatching { block() }.exceptionOrNull()?.also { failure = it }
        }
        failure?.let { throw it }
    }
}
