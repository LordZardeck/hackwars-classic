package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.BooleanCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetDummyPort.FUNCTION)
object SetDummyPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetDummyPort.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        val port = parsedCall.port
        context.computerHandler.addData(
            ApplicationData(BooleanCommandPayload(ApplicationCommand.of(SetDummyPort.FUNCTION), parsedCall.dummy ?: false), port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
