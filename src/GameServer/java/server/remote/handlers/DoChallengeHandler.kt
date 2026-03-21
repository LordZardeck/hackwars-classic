package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.DoChallenge
import game.ApplicationData
import game.payload.DoChallengePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.DOCHALLENGE)
object DoChallengeHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DoChallenge.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(DoChallengePayload(parsedCall.code.orEmpty(), parsedCall.challengeID.orEmpty()), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
