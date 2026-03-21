package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.HacktendoTarget
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.HACKTENDO_TARGET)
object HacktendoTargetHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = HacktendoTarget.fromRpc(rfc)
        val ip = parsedCall.ip
        context.computerHandler.addData(
            ApplicationData(parsedCall, 0, ip),
            ip,
            ApplicationData.INSIDE
        )
    }
}
