package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SellFileMulti.FUNCTION)
object SellFileMultiHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SellFileMulti.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val allFiles = parsedCall.allFiles
        val O: Array<Any?>? = arrayOf<Any?>(allFiles, ip)
        context.computerHandler.addData(
            ApplicationData(
                SellFileMulti.FUNCTION,
                O,
                0,
                "store" + context.serverID
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
