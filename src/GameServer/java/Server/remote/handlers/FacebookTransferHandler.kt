package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookTransfer.FUNCTION)
object FacebookTransferHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookTransfer.fromRpc(rfc)
        val ip = parsedCall.ip
        val ip2 = parsedCall.ip2

        val amount = parsedCall.amount
        val defaultPort = parsedCall.defaultPort

        val tO: Array<Any?>? = arrayOf<Any?>(ip2, amount)
        context.computerHandler.addData(
            ApplicationData(
                FacebookTransfer.FUNCTION,
                tO,
                defaultPort,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
