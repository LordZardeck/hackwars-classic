package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("setwatchonoff")
object SetwatchonoffHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            SetWatchOnOff.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val watchID =
            parsedCall.watchID
        val state =
            parsedCall.state
        val O: Any =
            arrayOf<Any?>(
                watchID,
                state
            )
        context.computerHandler.addData(
            ApplicationData(
                "setwatchonoff",
                O,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
