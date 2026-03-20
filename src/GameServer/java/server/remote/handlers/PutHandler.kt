package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.PutFilePayload
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
        val targetIP = context.crypt(parsedCall.targetIP)
        context.computerHandler.addData(
            ApplicationData(
                PutFilePayload(
                    targetIp = ip.orEmpty(),
                    name = name,
                    fetchPath = parsedCall.fetchPath.orEmpty(),
                    targetPath = parsedCall.putPath,
                    password = parsedCall.password.orEmpty(),
                    quantity = parsedCall.quantity ?: 1
                ),
                port,
                targetIP
            ),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
