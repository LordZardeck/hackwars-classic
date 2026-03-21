package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.Exit
import game.ApplicationCommand
import game.ApplicationData
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.EXIT)
object ExitHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Exit.fromRpc(rfc)
        val sourceIp = context.crypt(parsedCall.sourceIp)

        context.computerHandler.addData(
            ApplicationData(
                NoArgumentsPayload(ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.EXIT)),
                0,
                sourceIp
            ),
            parsedCall.targetIp,
            ApplicationData.OUTSIDE
        )
    }
}
