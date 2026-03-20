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

@RpcHandler(PeekCode.FUNCTION)
object PeekCodeHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = PeekCode.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(ApplicationCommand.of(PeekCode.FUNCTION)), parsedCall.port, ip),
            parsedCall.targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
