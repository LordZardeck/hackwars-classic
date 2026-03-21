package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.Vote
import game.ApplicationCommand
import game.ApplicationData
import game.payload.NoArgumentsPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.VOTE)
object VoteHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = Vote.fromRpc(rfc)
        val source_ip = context.crypt(parsedCall.sourceIp)

        context.computerHandler.addData(
            ApplicationData(
                NoArgumentsPayload(ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.VOTE)),
                0,
                parsedCall.targetIp.orEmpty()
            ),
            source_ip,
            ApplicationData.OUTSIDE
        )
    }
}
