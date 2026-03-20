package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.TransferPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookTransfer.FUNCTION)
object FacebookTransferHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookTransfer.fromRpc(rfc)
        val ip = parsedCall.ip.orEmpty()
        context.computerHandler.addData(
            ApplicationData(TransferPayload(parsedCall.ip2.orEmpty(), parsedCall.amount), parsedCall.defaultPort, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
