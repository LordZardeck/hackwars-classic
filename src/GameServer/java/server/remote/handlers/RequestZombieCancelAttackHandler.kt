package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestZombieCancelAttack
import game.ApplicationData
import game.payload.NoArgumentsPayload
import game.payload.REQUEST_CANCEL_ATTACK_COMMAND
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTZOMBIECANCELATTACK)
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
