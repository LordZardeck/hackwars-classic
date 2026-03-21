package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SetDefaultPort
import game.ApplicationCommand
import game.ApplicationData
import game.payload.IntCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SETDEFAULTPORT)
object SetDefaultPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val setDefaultPortCall = SetDefaultPort.fromRpc(rfc)
        val ip = context.crypt(setDefaultPortCall.encryptedIp)
        context.computerHandler.addData(
            ApplicationData(
                IntCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.SETDEFAULTPORT),
                    setDefaultPortCall.type ?: -1
                ),
                setDefaultPortCall.port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
