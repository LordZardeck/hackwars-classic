package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.MalGetPayload
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
        val attackPort = parsedCall.attackPort
        val targetIP = context.crypt(parsedCall.targetIP)
        context.computerHandler.addData(
            ApplicationData(
                MalGetPayload(
                    targetIp = targetIP,
                    name = parsedCall.name,
                    fetchPath = parsedCall.fetchPath.orEmpty(),
                    targetPath = parsedCall.putPath.orEmpty(),
                    password = "",
                    sourcePort = port,
                    attackPort = attackPort
                ),
                port,
                targetIP
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
