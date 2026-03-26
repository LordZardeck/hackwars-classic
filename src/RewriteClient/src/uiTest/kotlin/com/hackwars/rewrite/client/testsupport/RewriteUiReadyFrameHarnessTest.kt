package com.hackwars.rewrite.client.testsupport

import com.hackwars.rewrite.client.NoOpRewriteServiceSessionGateway
import com.hackwars.rewrite.client.RewriteRootFrame
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import java.awt.GraphicsEnvironment
import javax.swing.JInternalFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

class RewriteUiReadyFrameHarnessTest {
    @Test
    fun rewriteReadyFrameBootstrapsDesktopFromSnapshot() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = rewriteReadyFrame(
            sessionGateway = NoOpRewriteServiceSessionGateway,
            snapshot = ClientGameSnapshot(
                id = "192.0.2.10",
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
            ),
        )
        try {
            assertEquals("192.0.2.10", frame.controller.gameShellState()?.id)
            assertTrue(invokeAndWaitResult { frame.desktopPane.isShowing })
            assertEquals("rewrite-shell-menu-bar", invokeAndWaitResult { frame.jMenuBar?.name })
        } finally {
            disposeFrame(frame)
        }
    }

    @Test
    fun waitHelpersResolveNamedWindowsAndDialogs() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val frame = rewriteReadyFrame()
        val dialog = invokeAndWaitResult {
            javax.swing.JDialog(frame, "Harness Dialog").apply {
                name = "rewrite-harness-dialog"
                setSize(200, 100)
                isVisible = true
            }
        }
        try {
            invokeAndWait {
                frame.desktopPane.add(
                    JInternalFrame("Harness", true, true, true, true).apply {
                        name = "rewrite-harness-window"
                        setSize(240, 140)
                        isVisible = true
                    },
                )
            }

            assertEquals("rewrite-harness-window", waitForWindow(frame, "rewrite-harness-window").name)
            assertEquals("rewrite-harness-dialog", waitForDialog("rewrite-harness-dialog").name)
            assertEquals("Harness Dialog", waitForDialog("Harness Dialog").title)
        } finally {
            invokeAndWait { dialog.dispose() }
            disposeFrame(frame)
        }
    }
}
