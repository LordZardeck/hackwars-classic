package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FetchPorts.FUNCTION)
object FetchPortsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val fetchPortsCall = FetchPorts.fromRpc(rfc)
        val ip = context.crypt(fetchPortsCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(FetchPorts.FUNCTION, null, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
