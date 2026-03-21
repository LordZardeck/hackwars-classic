package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.DeleteMulti
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.DELETEMULTI)
object DeleteMultiHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteMulti.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
