package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.SavePagePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SavePage.FUNCTION)
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
