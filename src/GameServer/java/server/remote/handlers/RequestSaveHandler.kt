package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestSave
import game.ApplicationData
import game.payload.RequestSavePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTSAVE)
object RequestSaveHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestSave.fromRpc(rfc)

        @Suppress("UNCHECKED_CAST")
        val triggerParam = parsedCall.triggerParam as? HashMap<Any, Any>

        context.computerHandler.addData(
            ApplicationData(
                RequestSavePayload(parsedCall.fileName.orEmpty(), triggerParam),
                0,
                parsedCall.targetIP.orEmpty()
            ),
            parsedCall.targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
