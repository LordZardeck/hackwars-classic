package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.DoChallengePayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(DoChallenge.FUNCTION)
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
