package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.StringCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(CreateFolder.FUNCTION)
object CreateFolderHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = CreateFolder.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(StringCommandPayload(ApplicationCommand.of(CreateFolder.FUNCTION), parsedCall.directory), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
