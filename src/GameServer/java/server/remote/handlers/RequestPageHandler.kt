package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestPage.FUNCTION)
object RequestPageHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestPage.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        context.computerHandler.addData(
            ApplicationData(
                RequestPage.FUNCTION,
                null,
                0,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
