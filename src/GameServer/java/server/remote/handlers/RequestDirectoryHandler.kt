package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestDirectory
import game.ApplicationData
import game.payload.RequestDirectoryPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTDIRECTORY)
object RequestDirectoryHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestDirectory.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(RequestDirectoryPayload(parsedCall.path.orEmpty(), rfc.getID()), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
