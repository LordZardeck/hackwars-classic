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

@RpcHandler(PortOnOff.FUNCTION)
object PortOnOffHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = PortOnOff.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                BooleanCommandPayload(ApplicationCommand.of(PortOnOff.FUNCTION), parsedCall.on ?: false),
                parsedCall.port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
