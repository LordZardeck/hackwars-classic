package com.hackwars.rewrite.client.mvc

import javax.swing.JDialog
import javax.swing.JInternalFrame

/**
 * Locked post-login rewrite MVC contract.
 *
 * Package boundaries:
 * - `com.hackwars.rewrite.client.mvc` contains only shared architecture contracts, lifecycle helpers,
 *   and host or binding seams.
 * - Swing view classes should build components and render immutable [RewriteViewModel] instances only.
 * - Controller classes own selector subscriptions, listener binding, request routing, child-window
 *   launches, and disposal. Controllers must stay non-visual.
 * - App-level composition stays in root coordinators such as `RewriteRootController`; feature views
 *   must not import protocol brokers, stores, or coroutine infrastructure directly once their family
 *   reaches MVC completion.
 */
interface RewriteViewModel

fun interface RewriteView<in Model : RewriteViewModel> {
    fun render(model: Model)
}

interface RewriteController : AutoCloseable {
    override fun close() = Unit
}

abstract class RewriteControllerBase : RewriteController {
    private val closeActions = mutableListOf<() -> Unit>()
    private var closed = false

    protected fun onClose(action: () -> Unit) {
        if (closed) {
            action()
            return
        }
        closeActions += action
    }

    protected fun manage(closeable: AutoCloseable?) {
        if (closeable == null) {
            return
        }
        onClose { closeable.close() }
    }

    final override fun close() {
        if (closed) {
            return
        }
        closed = true
        closeActions
            .asReversed()
            .forEach { action -> runCatching { action() } }
        closeActions.clear()
        onClosed()
    }

    protected open fun onClosed() = Unit
}

data class RewriteFrameBinding(
    val frame: JInternalFrame,
    val controller: RewriteController? = null,
) {
    fun close() {
        controller?.close()
    }
}

data class RewriteDialogBinding(
    val dialog: JDialog,
    val controller: RewriteController? = null,
) {
    fun close() {
        controller?.close()
    }
}
