package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.GetFilePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(Get.FUNCTION)
object GetHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Get.fromRpc(rfc)
        val ip = parsedCall.ip
        val port = parsedCall.port
        val targetIP = context.crypt(parsedCall.targetIP)
        context.computerHandler.addData(
            ApplicationData(
                GetFilePayload(
                    targetIp = targetIP,
                    name = parsedCall.name,
                    fetchPath = parsedCall.fetchPath,
                    targetPath = parsedCall.putPath.orEmpty(),
                    password = parsedCall.password.orEmpty(),
                    quantity = parsedCall.quantity ?: 1
                ),
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
