package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FinalizeCancelled.FUNCTION)
object FinalizeCancelledHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FinalizeCancelled.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        context.computerHandler.addData(
            ApplicationData(FinalizeCancelled.FUNCTION, null, targetPort, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
