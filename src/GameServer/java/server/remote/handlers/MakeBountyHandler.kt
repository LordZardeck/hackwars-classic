package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.MakeBounty
import game.ApplicationData
import game.payload.CombatMakeBountyPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.MAKEBOUNTY)
object MakeBountyHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = MakeBounty.fromRpc(rfc)
        val source_ip = context.crypt(parsedCall.sourceIp)
        context.computerHandler.addData(
            ApplicationData(
                CombatMakeBountyPayload(
                    anonymous = parsedCall.anonymous ?: false,
                    target = parsedCall.target.orEmpty(),
                    type = parsedCall.type ?: 0,
                    fileName = parsedCall.fname.orEmpty(),
                    filePath = parsedCall.folder.orEmpty(),
                    iterations = parsedCall.iterations ?: 0,
                    reward = parsedCall.reward ?: 0.0f
                ),
                0,
                source_ip
            ),
            source_ip,
            ApplicationData.OUTSIDE
        )
    }
}
