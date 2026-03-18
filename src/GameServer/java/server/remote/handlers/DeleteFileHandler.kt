package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DeleteFile.FUNCTION)
object DeleteFileHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteFile.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val path = parsedCall.path
        val name = parsedCall.name
        val Parameter: Array<Any?>? = arrayOf<Any?>(path, name)
        context.computerHandler.addData(
            ApplicationData(DeleteFile.FUNCTION, Parameter, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
