package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SellFileMulti
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SELLFILEMULTI)
object SellFileMultiHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SellFileMulti.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), 0, "store" + context.serverID),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
