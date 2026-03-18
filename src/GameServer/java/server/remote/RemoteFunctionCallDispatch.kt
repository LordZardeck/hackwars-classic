package server.remote

import assignments.RemoteFunctionCall
import server.remote.generated.GeneratedRemoteCallRegistry

fun RemoteFunctionCall.invokeOnServer(context: RemoteCallContext): Boolean {
    return GeneratedRemoteCallRegistry.dispatch(this, context)
}
