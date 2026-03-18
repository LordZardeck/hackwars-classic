package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(CreateFolder.FUNCTION)
object CreateFolderHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = CreateFolder.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val directory = parsedCall.directory
        context.computerHandler.addData(
            ApplicationData(CreateFolder.FUNCTION, directory, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
