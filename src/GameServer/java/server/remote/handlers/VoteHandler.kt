package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(Vote.FUNCTION)
object VoteHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            Vote.fromRpc(rfc)
        val target_ip =
            parsedCall.targetIp
        var source_ip =
            parsedCall.sourceIp

        source_ip =
            context.crypt(
                source_ip)

        context.computerHandler.addData(
            ApplicationData(
                Vote.FUNCTION,
                null,
                0,
                target_ip
            ),
            source_ip,
            ApplicationData.OUTSIDE
        )
    }
}
