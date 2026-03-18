package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestPurchase.FUNCTION)
object RequestPurchaseHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestPurchase.fromRpc(rfc)
        var target_ip = parsedCall.targetIp
        var source_ip =
            parsedCall.sourceIp
        source_ip = context.crypt(source_ip)

        val file_name =
            parsedCall.fileName
        val quantity =
            parsedCall.quantity
        val O: Array<Any?>? =
            arrayOf<Any?>(file_name, quantity)

        if (target_ip.length >= 5) if (target_ip.substring(
                0,
                5
            ).lowercase(Locale.getDefault()) == "store"
        ) target_ip = "store" + context.serverID

        context.computerHandler.addData(
            ApplicationData(
                RequestPurchase.FUNCTION,
                O,
                0,
                source_ip
            ), target_ip, ApplicationData.OUTSIDE
        )
    }
}
