package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("unlock")
object UnlockHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            Unlock.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val code =
            parsedCall.code
        context.computerHandler.addData(
            ApplicationData(
                "unlock",
                code,
                0,
                ""
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
