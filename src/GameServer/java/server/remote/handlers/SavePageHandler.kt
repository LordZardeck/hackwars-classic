package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SavePage
import game.ApplicationData
import game.payload.SavePagePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SAVEPAGE)
object SavePageHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SavePage.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(SavePagePayload(parsedCall.title.orEmpty(), parsedCall.body.orEmpty()), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
