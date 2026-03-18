package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("dochallenge")
object DoChallengeHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = DoChallenge.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val code = parsedCall.code
        val challengeID = parsedCall.challengeID

        val O: Array<Any?>? = arrayOf<Any?>(code, challengeID)
        context.computerHandler.addData(
            ApplicationData("dochallenge", O, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
