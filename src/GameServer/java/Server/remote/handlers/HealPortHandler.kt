package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("healport")
object HealPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val healPortCall = HealPort.fromRpc(rfc)
        val ip = context.crypt(healPortCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData("heal", null, healPortCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
