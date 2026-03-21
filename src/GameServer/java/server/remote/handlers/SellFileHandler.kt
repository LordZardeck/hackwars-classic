package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SellFile
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SELLFILE)
object SellFileHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SellFile.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), 0, "store" + context.serverID),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
