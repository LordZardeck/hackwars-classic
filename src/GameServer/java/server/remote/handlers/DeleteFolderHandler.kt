package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DeleteFolder.FUNCTION)
object DeleteFolderHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteFolder.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val directory = parsedCall.directory
        context.computerHandler.addData(
            ApplicationData(DeleteFolder.FUNCTION, directory, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
