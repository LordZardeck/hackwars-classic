package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(MalGet.FUNCTION)
object MalGetHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = MalGet.fromRpc(rfc)
        val ip = parsedCall.ip
        val port = parsedCall.port
        val name = parsedCall.name
        val fetch_path = parsedCall.fetchPath
        val put_path = parsedCall.putPath
        var targetIP = parsedCall.targetIP
        val attackPort = parsedCall.attackPort
        targetIP = context.crypt(targetIP)
        val Parameter: Array<Any?>? =
            arrayOf<Any?>(targetIP, name, fetch_path, put_path, "", port, attackPort)
        context.computerHandler.addData(
            ApplicationData(MalGet.FUNCTION, Parameter, port, targetIP),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
