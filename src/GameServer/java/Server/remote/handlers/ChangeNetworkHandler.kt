package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(ChangeNetwork.FUNCTION)
object ChangeNetworkHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val changeNetworkCall = ChangeNetwork.fromRpc(rfc)
        val ip = context.crypt(changeNetworkCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(ChangeNetwork.FUNCTION, changeNetworkCall.network, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
