package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("requesttask")
object RequestTaskHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            RequestTask.fromRpc(rfc)
        val fileName = parsedCall.fileName
        val questID =
            parsedCall.questID
        val taskName =
            parsedCall.taskName
        val targetIP =
            parsedCall.targetIP
        val O: Any = arrayOf<Any?>(
            fileName,
            questID,
            taskName
        )
        context.computerHandler.addData(
            ApplicationData(
                "requesttask",
                O,
                0,
                targetIP
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
