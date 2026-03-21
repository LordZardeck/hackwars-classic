package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.FacebookTransfer
import game.ApplicationData
import game.payload.TransferPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.FACEBOOKTRANSFER)
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
