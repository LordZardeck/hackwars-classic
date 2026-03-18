package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetFilePrice.FUNCTION)
object SetFilePriceHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetFilePrice.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val path = parsedCall.path
        val name = parsedCall.name
        val price = parsedCall.price
        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name, price)
        context.computerHandler.addData(
            ApplicationData(SetFilePrice.FUNCTION, Parameter, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
