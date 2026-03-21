package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.Transfer
import game.ApplicationData
import game.payload.TransferPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.TRANSFER)
object TransferHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Transfer.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(TransferPayload(parsedCall.targetIp.orEmpty(), parsedCall.amount), parsedCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
