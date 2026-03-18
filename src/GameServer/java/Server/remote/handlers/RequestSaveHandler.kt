package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("requestsave")
object RequestSaveHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestSave.fromRpc(rfc)
        val fileName = parsedCall.fileName
        val TriggerParam =
            parsedCall.triggerParam
        val targetIP =
            parsedCall.targetIP
        val O: Any = arrayOf<Any?>(
            fileName,
            TriggerParam
        )

        context.computerHandler.addData(
            ApplicationData(
                "requestsave",
                O,
                0,
                targetIP
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
