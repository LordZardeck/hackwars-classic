package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.DeleteWatch
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.DELETEWATCH)
object DeleteWatchHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteWatch.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
