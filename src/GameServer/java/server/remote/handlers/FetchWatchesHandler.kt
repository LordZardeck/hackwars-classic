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

@RpcHandler(FetchWatches.FUNCTION)
object FetchWatchesHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = FetchWatches.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(FetchWatches.FUNCTION)), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
