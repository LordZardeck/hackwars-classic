package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.ReplaceApplication
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REPLACEAPPLICATION)
object ReplaceApplicationHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = ReplaceApplication.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), parsedCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
