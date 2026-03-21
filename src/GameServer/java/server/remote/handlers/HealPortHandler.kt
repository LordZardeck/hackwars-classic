package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.HealPort
import game.ApplicationData
import game.payload.HealPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.HEALPORT)
object HealPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val healPortCall = HealPort.fromRpc(rfc)
        val ip = context.crypt(healPortCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(HealPayload, healPortCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
