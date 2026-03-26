package com.hackwars.rewrite.client.testsupport

import com.hackwars.rewrite.client.DeterministicRewriteLoginAuthGateway
import com.hackwars.rewrite.client.NoOpRewriteServiceSessionGateway
import com.hackwars.rewrite.client.RewriteRootController
import com.hackwars.rewrite.client.RewriteRootFrame
import com.hackwars.rewrite.client.RewriteServiceSessionGateway
import com.hackwars.rewrite.clientdev.RewriteClientDevEnvironment
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.RewriteClientJson
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.time.Instant
import javax.swing.JDialog
import javax.swing.JInternalFrame
import javax.swing.JTextField
import javax.swing.SwingUtilities

private const val DEFAULT_AUTH_CONNECTION_ID = "conn-1"
private const val DEFAULT_AUTH_PLAYFAB_ID = "PF-LOCAL"
private const val DEFAULT_AUTH_PLAYER_IP = "192.0.2.10"
private val DEFAULT_AUTH_ACCEPTED_AT: Instant = Instant.parse("2026-03-25T00:00:00Z")

fun rewriteUiAuthenticatedDesktopFrame(
    sessionGateway: RewriteServiceSessionGateway = NoOpRewriteServiceSessionGateway,
    snapshot: ClientGameSnapshot? = null,
    connectionId: String = DEFAULT_AUTH_CONNECTION_ID,
    playFabId: String = DEFAULT_AUTH_PLAYFAB_ID,
    playerIp: String = DEFAULT_AUTH_PLAYER_IP,
    sessionStartedAt: Instant = DEFAULT_AUTH_ACCEPTED_AT,
): RewriteRootFrame = rewriteReadyFrame(
    sessionGateway = sessionGateway,
    snapshot = snapshot,
    connectionId = connectionId,
    playFabId = playFabId,
    playerIp = playerIp,
    sessionStartedAt = sessionStartedAt,
)

fun rewriteReadyFrame(
    sessionGateway: RewriteServiceSessionGateway = NoOpRewriteServiceSessionGateway,
    snapshot: ClientGameSnapshot? = null,
    connectionId: String = DEFAULT_AUTH_CONNECTION_ID,
    playFabId: String = DEFAULT_AUTH_PLAYFAB_ID,
    playerIp: String = DEFAULT_AUTH_PLAYER_IP,
    sessionStartedAt: Instant = DEFAULT_AUTH_ACCEPTED_AT,
): RewriteRootFrame {
    val frame = invokeAndWaitResult {
        RewriteRootFrame(
            controller = RewriteRootController(
                authGateway = DeterministicRewriteLoginAuthGateway(),
                sessionGateway = sessionGateway,
            ),
        ).apply { isVisible = true }
    }
    frame.controller.store.showDesktop()
    frame.controller.accept(
        RewriteService.GAME,
        RewriteFrames.authAccepted(
            connectionId = connectionId,
            playFabId = playFabId,
            playerIp = playerIp,
            heartbeatInterval = kotlin.time.Duration.parse("15s"),
            sessionStartedAt = sessionStartedAt,
        ),
    )
    snapshot?.let { frame.controller.accept(RewriteService.GAME, snapshotFrame(it)) }
    waitUntil {
        invokeAndWaitResult {
            frame.desktopPane.isShowing && frame.jMenuBar != null
        }
    }
    return frame
}

fun rewriteUiDeterministicDevModeFrame(
    environment: RewriteClientDevEnvironment,
    email: String = environment.fixture.login.email,
    password: CharArray = environment.fixture.login.password.toCharArray(),
    timeoutMillis: Long = 5_000L,
): RewriteRootFrame = rewriteDevReadyFrame(
    environment = environment,
    email = email,
    password = password,
    timeoutMillis = timeoutMillis,
)

fun rewriteDevReadyFrame(
    environment: RewriteClientDevEnvironment,
    email: String = environment.fixture.login.email,
    password: CharArray = environment.fixture.login.password.toCharArray(),
    timeoutMillis: Long = 5_000L,
): RewriteRootFrame {
    val frame = invokeAndWaitResult {
        RewriteRootFrame(
            controller = environment.createController(),
        ).apply { isVisible = true }
    }
    frame.controller.submitLogin(email, password)
    waitUntil(timeoutMillis) {
        frame.controller.route() == RewriteClientRoute.DESKTOP
    }
    waitUntil(timeoutMillis) {
        invokeAndWaitResult { frame.desktopPane.isShowing && frame.jMenuBar != null }
    }
    return frame
}

fun waitForWindow(
    frame: RewriteRootFrame,
    windowName: String,
    timeoutMillis: Long = 3_000L,
): JInternalFrame {
    waitUntil(timeoutMillis) {
        invokeAndWaitResult { frame.desktopPane.allFrames.any { it.name == windowName } }
    }
    return invokeAndWaitResult {
        frame.desktopPane.allFrames.first { it.name == windowName }
    }
}

fun rewriteUiWaitForWindow(
    frame: RewriteRootFrame,
    windowName: String,
    timeoutMillis: Long = 3_000L,
): JInternalFrame = waitForWindow(frame, windowName, timeoutMillis)

fun waitForDialog(
    dialogKey: String,
    timeoutMillis: Long = 3_000L,
): JDialog {
    waitUntil(timeoutMillis) {
        Window.getWindows()
            .filterIsInstance<JDialog>()
            .any { it.isDisplayable && (it.name == dialogKey || it.title == dialogKey) }
    }
    return Window.getWindows()
        .filterIsInstance<JDialog>()
        .first { it.isDisplayable && (it.name == dialogKey || it.title == dialogKey) }
}

fun rewriteUiWaitForDialog(
    dialogKey: String,
    timeoutMillis: Long = 3_000L,
): JDialog = waitForDialog(dialogKey, timeoutMillis)

fun disposeFrame(frame: RewriteRootFrame) {
    invokeAndWait {
        frame.isVisible = false
        frame.dispose()
    }
}

fun rewriteUiDisposeFrame(frame: RewriteRootFrame) = disposeFrame(frame)

inline fun invokeAndWait(crossinline block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        block()
        return
    }
    var failure: Throwable? = null
    SwingUtilities.invokeAndWait {
        runCatching { block() }
            .onFailure { failure = it }
    }
    failure?.let { throw it }
}

inline fun rewriteUiInvokeAndWait(crossinline block: () -> Unit) = invokeAndWait(block)

fun <T> invokeAndWaitResult(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }
    var result: T? = null
    var failure: Throwable? = null
    SwingUtilities.invokeAndWait {
        runCatching { block() }
            .onSuccess { result = it }
            .onFailure { failure = it }
    }
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

fun <T> rewriteUiInvokeAndWaitResult(block: () -> T): T = invokeAndWaitResult(block)

fun waitUntil(
    timeoutMillis: Long = 3_000L,
    predicate: () -> Boolean,
) {
    val startedAt = System.currentTimeMillis()
    while (System.currentTimeMillis() - startedAt < timeoutMillis) {
        flushEdt()
        if (predicate()) {
            return
        }
        Thread.sleep(25)
    }
    flushEdt()
    check(predicate()) { "Condition was not met within ${timeoutMillis}ms." }
}

fun rewriteUiWaitUntil(
    timeoutMillis: Long = 3_000L,
    predicate: () -> Boolean,
) = waitUntil(timeoutMillis, predicate)

fun flushEdt() {
    if (SwingUtilities.isEventDispatchThread()) {
        return
    }
    SwingUtilities.invokeAndWait {}
}

fun rewriteUiFlushEdt() = flushEdt()

fun findComponent(
    root: Component,
    name: String,
): Component? {
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

fun rewriteUiFindNamedComponent(
    root: Component,
    name: String,
): Component? = findComponent(root, name)

fun findComponents(root: Component): List<Component> {
    return buildList {
        add(root)
        if (root is Container) {
            root.components.forEach { child ->
                addAll(findComponents(child))
            }
        }
    }
}

fun rewriteUiFindComponents(root: Component): List<Component> = findComponents(root)

fun rewriteUiAllComponents(root: Component): List<Component> = findComponents(root)

inline fun <reified T : Component> requireNamedComponent(
    root: Component,
    name: String,
): T {
    return findComponent(root, name) as? T
        ?: error("Unable to find ${T::class.simpleName} named $name")
}

inline fun <reified T : Component> rewriteUiRequireNamedComponent(
    root: Component,
    name: String,
): T = requireNamedComponent(root, name)

fun setSegmentedIp(
    root: Component,
    ip: String,
) {
    val segments = ip.split('.')
    require(segments.size == 4) { "Expected dotted-quad IPv4 address, got $ip" }
    segments.forEachIndexed { index, value ->
        requireNamedComponent<JTextField>(root, "rewrite-economy-ip-segment-$index").text = value
    }
}

fun rewriteUiSetSegmentedIp(
    root: Component,
    ip: String,
) = setSegmentedIp(root, ip)

fun rewriteUiSnapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope = snapshotFrame(snapshot)

private fun snapshotFrame(snapshot: ClientGameSnapshot): FrameEnvelope {
    return RewriteFrames.snapshot(
        gameStateId = snapshot.id,
        sequence = snapshot.version,
        payload = RewriteClientJson.encode(ClientGameSnapshot.serializer(), snapshot),
    )
}
