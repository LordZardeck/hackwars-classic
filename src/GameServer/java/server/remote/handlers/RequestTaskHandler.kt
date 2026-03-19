package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestTask.FUNCTION)
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
                RequestTask.FUNCTION,
                O,
                0,
                targetIP
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
