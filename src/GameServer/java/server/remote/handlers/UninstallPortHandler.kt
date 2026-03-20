package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.IntCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(UninstallPort.FUNCTION)
object UninstallPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = UninstallPort.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(IntCommandPayload(ApplicationCommand.of(UninstallPort.FUNCTION), parsedCall.port), parsedCall.port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
