package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestGame.FUNCTION)
object RequestGameHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestGame.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val path = parsedCall.path
        val name = parsedCall.name
        val Parameter: Array<String?>? = arrayOf<String?>(path, name)
        context.computerHandler.addData(
            ApplicationData(RequestGame.FUNCTION, Parameter, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
