package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("exit")
object ExitHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            Exit.fromRpc(rfc)
        val target_ip =
            parsedCall.targetIp
        var source_ip =
            parsedCall.sourceIp

        source_ip = context.crypt(
            source_ip)

        context.computerHandler.addData(
            ApplicationData(
                "exit",
                null,
                0,
                source_ip
            ),
            target_ip,
            ApplicationData.OUTSIDE
        )
    }
}
