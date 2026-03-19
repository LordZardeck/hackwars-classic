package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestTrigger.FUNCTION)
object RequestTriggerHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestTrigger.fromRpc(rfc)
        val watchNote = parsedCall.watchNote
        val TriggerParam =
            parsedCall.triggerParam
        val sourceIP =
            parsedCall.sourceIP
        val targetIP =
            parsedCall.targetIP
        val O: Any = arrayOf<Any?>(
            watchNote,
            TriggerParam,
            sourceIP
        )
        context.computerHandler.addData(
            ApplicationData(
                RequestTrigger.FUNCTION,
                O,
                0,
                sourceIP
            ), targetIP, ApplicationData.OUTSIDE
        )
    }
}
