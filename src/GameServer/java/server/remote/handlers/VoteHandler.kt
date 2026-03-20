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

@RpcHandler(Vote.FUNCTION)
object VoteHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Vote.fromRpc(rfc)
        val source_ip = context.crypt(parsedCall.sourceIp)

        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(Vote.FUNCTION)), 0, parsedCall.targetIp.orEmpty()),
            source_ip,
            ApplicationData.OUTSIDE
        )
    }
}
