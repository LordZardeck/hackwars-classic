package server.remote

import assignments.RemoteFunctionCall

fun interface RemoteCallHandler {
    fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext)
}
