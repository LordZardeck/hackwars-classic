package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("setwatchobservedports")
object SetwatchobservedportsHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            SetWatchObservedPorts.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val watchID =
            parsedCall.watchID
        val ObservedPorts =
            parsedCall.observedPorts
        val Parameter: Array<Any?>? =
            arrayOf<Any?>(
                watchID,
                ObservedPorts
            )
        context.computerHandler.addData(
            ApplicationData(
                "setwatchobservedports",
                Parameter,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
