package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookUpdate.FUNCTION)
object FacebookUpdateHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookUpdate.fromRpc(rfc)
        val ip = parsedCall.ip
        context.computerHandler.addData(
            ApplicationData(
                FacebookUpdate.FUNCTION,
                null,
                0,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
