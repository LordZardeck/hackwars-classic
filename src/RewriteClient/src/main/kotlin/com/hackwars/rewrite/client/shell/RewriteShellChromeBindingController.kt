package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.mvc.RewriteControllerBase
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RewriteShellChromeBindingController(
    shellStateFlow: Flow<ClientGameSnapshot?>,
    initialShellState: ClientGameSnapshot?,
    acceptedPlayerIpFlow: Flow<String?>,
    initialAcceptedPlayerIp: String?,
    private val shellChromeController: RewriteShellChromeController,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RewriteControllerBase() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        onClose { scope.cancel() }
        shellChromeController.updateShellState(initialShellState)
        shellChromeController.updateAcceptedPlayerIp(initialAcceptedPlayerIp)
        scope.launch {
            shellStateFlow.collect { shellState ->
                runOnEdt {
                    shellChromeController.updateShellState(shellState)
                }
            }
        }
        scope.launch {
            acceptedPlayerIpFlow.collect { playerIp ->
                runOnEdt {
                    shellChromeController.updateAcceptedPlayerIp(playerIp)
                }
            }
        }
    }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
