package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetDefaultPort.FUNCTION)
object SetDefaultPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val setDefaultPortCall = SetDefaultPort.fromRpc(rfc)
        val ip = context.crypt(setDefaultPortCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(SetDefaultPort.FUNCTION, setDefaultPortCall.type, setDefaultPortCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
