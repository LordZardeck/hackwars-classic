package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestTrigger
import game.ApplicationData
import game.payload.TriggerWatchByNotePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTTRIGGER)
object RequestTriggerHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestTrigger.fromRpc(rfc)

        @Suppress("UNCHECKED_CAST")
        val triggerParam = parsedCall.triggerParam as? HashMap<Any, Any>
        context.computerHandler.addData(
            ApplicationData(
                TriggerWatchByNotePayload(
                    parsedCall.watchNote.orEmpty(),
                    triggerParam,
                    parsedCall.sourceIP.orEmpty()
                ),
                0,
                parsedCall.sourceIP.orEmpty()
            ),
            parsedCall.targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
