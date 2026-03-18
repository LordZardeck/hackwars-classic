package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(Put.FUNCTION)
object PutHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Put.fromRpc(rfc)
        val ip = parsedCall.ip
        val port = parsedCall.port
        val name = parsedCall.name
        val fetch_path = parsedCall.fetchPath
        val put_path = parsedCall.putPath
        var targetIP = parsedCall.targetIP
        targetIP = context.crypt(targetIP)
        val password = parsedCall.password
        val quantity = parsedCall.quantity
        val Parameter: Array<Any?>? =
            arrayOf<Any?>(ip, name, fetch_path, put_path, password, quantity)
        context.computerHandler.addData(
            ApplicationData(Put.FUNCTION, Parameter, port, targetIP),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
