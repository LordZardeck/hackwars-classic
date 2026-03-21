package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.FinalizeCancelled
import game.ApplicationData
import game.payload.FinalizeCancelledPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.FINALIZECANCELLED)
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
