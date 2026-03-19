package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DeleteMulti.FUNCTION)
object DeleteMultiHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteMulti.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)

        val allFiles = parsedCall.allFiles
        val parameters: Array<Any?>? = arrayOf<Any?>(allFiles)
        context.computerHandler.addData(
            ApplicationData(DeleteMulti.FUNCTION, parameters, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
