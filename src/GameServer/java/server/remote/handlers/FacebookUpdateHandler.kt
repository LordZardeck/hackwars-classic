package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(FacebookUpdate.FUNCTION)
object FacebookUpdateHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FacebookUpdate.fromRpc(rfc)
        val ip = parsedCall.ip.orEmpty()
        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(FacebookUpdate.FUNCTION)), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
