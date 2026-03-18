package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DecompileFile.FUNCTION)
object DecompileFileHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DecompileFile.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val location = parsedCall.location
        val fileName = parsedCall.fileName
        val compileCost = parsedCall.compileCost
        val O: Array<Any?>? = arrayOf<Any?>(location, fileName, compileCost, ip)
        context.computerHandler.addData(
            ApplicationData(DecompileFile.FUNCTION, O, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
