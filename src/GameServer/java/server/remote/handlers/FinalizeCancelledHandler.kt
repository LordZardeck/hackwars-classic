package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.FinalizeCancelledPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FinalizeCancelled.FUNCTION)
object FinalizeCancelledHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FinalizeCancelled.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        context.computerHandler.addData(
            ApplicationData(FinalizeCancelledPayload, targetPort, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
