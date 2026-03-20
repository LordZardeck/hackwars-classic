package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.RequestSavePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestSave.FUNCTION)
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
