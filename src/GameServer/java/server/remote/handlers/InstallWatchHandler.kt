package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.InstallWatch
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.INSTALLWATCH)
object InstallWatchHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = InstallWatch.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), parsedCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
