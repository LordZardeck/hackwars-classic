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

@RpcHandler(Exit.FUNCTION)
object ExitHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Exit.fromRpc(rfc)
        val sourceIp = context.crypt(parsedCall.sourceIp)

        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(Exit.FUNCTION)), 0, sourceIp),
            parsedCall.targetIp,
            ApplicationData.OUTSIDE
        )
    }
}
