package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellCommand
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientEconomyState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientRuntimeState
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.time.Instant
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteRootFrameUiTest {
    @Test
    fun rootFrameStartsOnLoginSurface() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            assertEquals(RewriteClientRoute.LOGIN, frame.controller.route())
            assertNotNull(frame.loginScene)
            assertNotNull(frame.desktopPane)
            assertNotNull(frame.shellHost)
            assertNull(frame.jMenuBar)
            assertEquals("rewrite-root-login", frame.loginScene.name)
            assertTrue(frame.loginScene.isShowing)
            assertFalse(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
            assertEquals("Hack Wars", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun frameSwitchesToDesktopWhenControllerShowsDesktop() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }
            assertTrue(frame.desktopPane.isShowing)
            assertTrue(frame.shellHost.isShowing)
            assertEquals("rewrite-shell-desktop", frame.desktopPane.name)
            assertEquals("rewrite-shell-menu-bar", frame.jMenuBar?.name)
            assertTrue(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
            assertEquals("Hack Wars", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun frameKeepsMenuBarHiddenWhileBootstrapping() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.beginGameBootstrap()
            waitUntil { frame.loginScene.isShowing }
            assertTrue(frame.loginScene.isShowing)
            assertNull(frame.jMenuBar)
            assertFalse(frame.shellHost.statsRail.isVisible)
            assertFalse(frame.shellHost.countdownLabel.isVisible)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun desktopChromeRendersBoundStatsValuesAndCountdownTitle() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.authAccepted(
                    connectionId = "conn-1",
                    playFabId = "PF-LOCAL",
                    playerIp = "LOCAL-IP",
                    heartbeatInterval = kotlin.time.Duration.parse("15s"),
                    sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = ""),
                        economy = ClientEconomyState(
                            pettyCash = 125.5,
                            bankMoney = 88.25,
                            commodities = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
                        ),
                        runtime = ClientRuntimeState(countdownSeconds = 90),
                    ),
                ),
            )

            waitUntil {
                frame.shellHost.statsRail.isVisible &&
                    frame.shellHost.countdownLabel.isVisible &&
                    frame.title == "Server Shutdown in 1:30"
            }

            assertEquals("LOCAL-IP", label(frame.shellHost.statsRail, "rewrite-shell-stats-ip").text)
            assertEquals("$125.50", label(frame.shellHost.statsRail, "rewrite-shell-stats-petty-cash").text)
            assertEquals("$88.25", label(frame.shellHost.statsRail, "rewrite-shell-stats-bank").text)
            assertEquals("1", label(frame.shellHost.statsRail, "rewrite-shell-stats-commodity-duct-tape").text)
            assertEquals("5", label(frame.shellHost.statsRail, "rewrite-shell-stats-commodity-plutonium").text)
            assertEquals("Server Shutdown in 1:30", frame.shellHost.countdownLabel.text)
            assertEquals("Server Shutdown in 1:30", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun clearedCountdownResetsDesktopChromeBackToPlayerTitle() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            frame.controller.accept(
                RewriteService.GAME,
                RewriteFrames.authAccepted(
                    connectionId = "conn-1",
                    playFabId = "PF-LOCAL",
                    playerIp = "LOCAL-IP",
                    heartbeatInterval = kotlin.time.Duration.parse("15s"),
                    sessionStartedAt = Instant.parse("2026-03-25T00:00:00Z"),
                ),
            )
            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                        runtime = ClientRuntimeState(countdownSeconds = 5),
                    ),
                ),
            )
            waitUntil { frame.shellHost.countdownLabel.isVisible }

            frame.controller.accept(
                RewriteService.GAME,
                snapshotFrame(
                    ClientGameSnapshot(
                        id = "LOCAL-IP",
                        identity = ClientComputerIdentity(playerIp = "LOCAL-IP"),
                        runtime = ClientRuntimeState(countdownSeconds = 0),
                    ),
                ),
            )

            waitUntil {
                !frame.shellHost.countdownLabel.isVisible &&
                    frame.title == "Hack Wars - LOCAL-IP"
            }
            assertEquals("Hack Wars - LOCAL-IP", frame.title)
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun shellCanOpenAndMinimizePlaceholderWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply { isVisible = true }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }

            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            }
            waitUntil { frame.desktopPane.allFrames.size == 1 }
            val placeholderFrame = frame.desktopPane.allFrames.single()
            SwingUtilities.invokeAndWait {
                placeholderFrame.isIcon = true
            }

            waitUntil { frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 1 }
            assertEquals(1, frame.shellHost.menuBar.taskBar.minimizedApplicationCount())

            SwingUtilities.invokeAndWait {
                frame.controller.launchShellCommand(RewriteShellCommand.DEPOSIT)
            }
            waitUntil {
                frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 0 &&
                    !placeholderFrame.isIcon
            }
            assertEquals(0, frame.shellHost.menuBar.taskBar.minimizedApplicationCount())
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun shellTaskBarScrollButtonsEnableWhenManyFramesAreMinimized() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = invokeAndWaitResult {
            RewriteRootFrame(
                controller = RewriteRootController(
                    authGateway = DeterministicRewriteLoginAuthGateway(),
                    sessionGateway = NoOpRewriteServiceSessionGateway,
                ),
            ).apply {
                setSize(900, 700)
                isVisible = true
            }
        }
        try {
            frame.controller.store.showDesktop()
            waitUntil { frame.desktopPane.isShowing && frame.jMenuBar != null }

            SwingUtilities.invokeAndWait {
                RewriteShellCommand.entries.take(10).forEach { command ->
                    frame.controller.launchShellCommand(command)
                    frame.desktopPane.allFrames
                        .first { it.name == "rewrite-shell-window-${command.stableId}" }
                        .isIcon = true
                }
            }

            waitUntil {
                frame.shellHost.menuBar.taskBar.minimizedApplicationCount() == 10 &&
                    frame.shellHost.menuBar.taskBar.rightScrollButton.isEnabled
            }
            assertTrue(frame.shellHost.menuBar.taskBar.rightScrollButton.isEnabled)
        } finally {
            disposeFrame(frame)
        }
    }

    private fun disposeFrame(frame: RewriteRootFrame) {
        SwingUtilities.invokeAndWait {
            frame.dispose()
        }
    }

    private inline fun <T> invokeAndWaitResult(crossinline block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching { block() }
        }
        return result!!.getOrThrow()
    }

    private fun waitUntil(timeoutMillis: Long = 3_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(20)
        }
        assertTrue(predicate())
    }

    private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
        return RewriteFrames.snapshot(
            gameStateId = snapshot.id,
            sequence = snapshot.version,
            payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
        )
    }

    private fun label(root: Component, name: String): JLabel {
        return findComponent(root, name) as? JLabel
            ?: error("Unable to find JLabel named $name")
    }

    private fun findComponent(root: Component, name: String): Component? {
        if (root.name == name) {
            return root
        }
        if (root is Container) {
            root.components.forEach { child ->
                findComponent(child, name)?.let { return it }
            }
        }
        return null
    }
}
