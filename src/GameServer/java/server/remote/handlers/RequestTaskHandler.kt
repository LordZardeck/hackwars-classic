package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.RequestTaskPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestTask.FUNCTION)
object RequestTaskHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestTask.fromRpc(rfc)
        val questId = parsedCall.questID?.toIntOrNull() ?: return
        context.computerHandler.addData(
            ApplicationData(
                RequestTaskPayload(parsedCall.fileName, questId, parsedCall.taskName.orEmpty()),
                0,
                parsedCall.targetIP.orEmpty()
            ),
            parsedCall.targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
