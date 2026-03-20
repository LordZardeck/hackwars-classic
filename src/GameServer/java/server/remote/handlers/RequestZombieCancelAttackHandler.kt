package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.NoArgumentsPayload
import game.payload.REQUEST_CANCEL_ATTACK_COMMAND
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestZombieCancelAttack.FUNCTION)
object RequestZombieCancelAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestZombieCancelAttack.fromRpc(rfc)
        val targetIP = context.crypt(parsedCall.targetIP)
        context.computerHandler.addData(
            ApplicationData(NoArgumentsPayload(REQUEST_CANCEL_ATTACK_COMMAND), parsedCall.port, targetIP),
            parsedCall.ip,
            ApplicationData.OUTSIDE
        )
    }
}
