package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(Transfer.FUNCTION)
object TransferHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Transfer.fromRpc(rfc)
        val amount = parsedCall.amount
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val target_ip = parsedCall.targetIp
        val port = parsedCall.port
        val tO: Array<Any?>? = arrayOf<Any?>(target_ip, amount)
        context.computerHandler.addData(
            ApplicationData(Transfer.FUNCTION, tO, port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
