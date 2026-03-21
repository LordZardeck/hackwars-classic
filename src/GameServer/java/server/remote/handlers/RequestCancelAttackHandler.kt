package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestCancelAttack
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTCANCELATTACK)
object RequestCancelAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestCancelAttack.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(parsedCall.copy(ip = ip), parsedCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
