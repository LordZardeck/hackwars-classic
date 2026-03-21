package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SetDummyPort
import game.ApplicationCommand
import game.ApplicationData
import game.payload.BooleanCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SETDUMMYPORT)
object SetDummyPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetDummyPort.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        val port = parsedCall.port
        context.computerHandler.addData(
            ApplicationData(
                BooleanCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.SETDUMMYPORT),
                    parsedCall.dummy ?: false
                ), port, ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
