package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("cluedata")
object CluedataHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            ClueData.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val data =
            parsedCall.data
        context.computerHandler.addData(
            ApplicationData(
                "cluedata",
                data,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
