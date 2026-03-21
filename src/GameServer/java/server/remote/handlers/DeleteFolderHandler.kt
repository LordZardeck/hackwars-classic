package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.DeleteFolder
import game.ApplicationCommand
import game.ApplicationData
import game.payload.StringCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.DELETEFOLDER)
object DeleteFolderHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DeleteFolder.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                StringCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.DELETEFOLDER),
                    parsedCall.directory
                ), 0, ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
