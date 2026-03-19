package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SellFile.FUNCTION)
object SellFileHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SellFile.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val location = parsedCall.location
        val fileName = parsedCall.fileName
        val compileCost = parsedCall.compileCost
        val quantity = parsedCall.quantity
        val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip, quantity)
        context.computerHandler.addData(
            ApplicationData(
                SellFile.FUNCTION,
                O,
                0,
                "store" + context.serverID
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
