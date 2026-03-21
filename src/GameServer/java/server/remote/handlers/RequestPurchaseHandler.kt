package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestPurchase
import game.ApplicationData
import game.payload.RequestPurchasePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTPURCHASE)
object RequestPurchaseHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestPurchase.fromRpc(rfc)
        var target_ip = parsedCall.targetIp
        val source_ip = context.crypt(parsedCall.sourceIp)

        if (target_ip.length >= 5) if (target_ip.substring(
                0,
                5
            ).lowercase(Locale.getDefault()) == "store"
        ) target_ip = "store" + context.serverID

        context.computerHandler.addData(
            ApplicationData(
                RequestPurchasePayload(parsedCall.fileName.orEmpty(), parsedCall.quantity ?: 0),
                0,
                source_ip
            ),
            target_ip,
            ApplicationData.OUTSIDE
        )
    }
}
